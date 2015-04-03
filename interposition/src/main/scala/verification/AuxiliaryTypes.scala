package akka.dispatch.verification

import akka.actor.{ ActorCell, ActorRef, Props }
import akka.dispatch.{ Envelope }

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Semaphore,
       scala.collection.mutable.HashMap,
       scala.collection.mutable.HashSet,
       scala.collection.mutable.Queue


object IDGenerator {
  var uniqueId = new AtomicInteger // DPOR root event is assumed to be ID 0, incrementAndGet ensures starting at 1

  def get() : Integer = {
    return uniqueId.incrementAndGet()
  }
}

       
final case class Unique(
  val event : Event,
  var id : Int = IDGenerator.get()
) extends ExternalEvent {
  
  override def equals (other: Any) = other match {
    case u: Unique if (u.id == this.id) => true
    case u: Unique => false
    case _ => throw new Exception("not a Unique event")
  }
  
}

case class Uniq[E](
  val element : E,
  var id : Int = IDGenerator.get()
)

// Message delivery -- (not the initial send)
// N.B., if an event trace was serialized, it's possible that msg is of type
// MessageFingerprint rather than a whole message!
case class MsgEvent(
    sender: String, receiver: String, msg: Any) extends Event

case class SpawnEvent(
    parent: String, props: Props, name: String, actor: ActorRef) extends Event

case class NetworkPartition(
    first: Set[String], 
    second: Set[String]) extends Event with ExternalEvent

case object RootEvent extends Event

//case object DporQuiescence extends Event with ExternalEvent



// Base class for failure detector messages
abstract class FDMessage

// Notification telling a node that it can query a failure detector by sending messages to fdNode.
case class FailureDetectorOnline(fdNode: String) extends FDMessage

// A node is unreachable, either due to node failure or partition.
case class NodeUnreachable(actor: String) extends FDMessage with Event

case class NodesUnreachable(actors: Set[String]) extends FDMessage with Event


// A new node is now reachable, either because a partition healed or an actor spawned.
case class NodeReachable(actor: String) extends FDMessage

// Query the failure detector for currently reachable actors.
case object QueryReachableGroup extends FDMessage

// Response to failure detector queries.
case class ReachableGroup(actors: Set[String]) extends FDMessage

object MessageTypes {
  // Messages that the failure detector sends to actors.
  // Assumes that actors don't relay fd messages to eachother.
  def fromFailureDetector(m: Any) : Boolean = {
    m match {
      case _: FailureDetectorOnline | _: NodeUnreachable | _: NodeReachable |
           _: ReachableGroup => return true
      case _ => return false
    }
  }
}


trait TellEnqueue {
  def tell()
  def enqueue()
  def reset()
  def await ()
}


class TellEnqueueBusyWait extends TellEnqueue {
  
  var enqueue_count = new AtomicInteger
  var tell_count = new AtomicInteger
  
  def tell() {
    tell_count.incrementAndGet()
  }
  
  def enqueue() {
    enqueue_count.incrementAndGet()
  }
  
  def reset() {
    tell_count.set(0)
    enqueue_count.set(0)
  }

  def await () {
    while (tell_count.get != enqueue_count.get) {}
  }
  
}
    

class TellEnqueueSemaphore extends Semaphore(1) with TellEnqueue {
  
  var enqueue_count = new AtomicInteger
  var tell_count = new AtomicInteger
  
  def tell() {
    tell_count.incrementAndGet()
    reducePermits(1)
    require(availablePermits() <= 0)
  }

  def enqueue() {
    enqueue_count.incrementAndGet()
    require(availablePermits() <= 0)
    release()
  }
  
  def reset() {
    tell_count.set(0)
    enqueue_count.set(0)
    // Set available permits to 0
    drainPermits() 
    // Add a permit
    release()
  }
  
  def await() {
    acquire
    release
  }
}


class ExploredTacker {
  private[this] var exploredStack = new HashMap[Int, (HashSet[(Unique, Unique)], HashSet[Int]) ]
  private[this] val exploredSeq = new HashSet[Vector[Int]]
  
  private[this] var currentTrace = new Queue[Unique]
  private[this] var prevTrace = new Queue[Unique]
  private[this] val nextTrace = new Queue[Unique]

  object filter {
    
    def inUpcomingEvents(
        set : scala.collection.immutable.Set[Unique],
        t : (Unique, ActorCell, Envelope)) : Boolean =
      return !(set.map { x => x.id } contains t._1.id) &&
             !alreadyExplored(t)
    
    def alreadyExploredImpl(id: Int) : Boolean = {
      exploredStack.get(currentTrace.size - 1) match {
        case Some((set, branchSet)) =>
          return branchSet.contains(id)
        case None =>
          return false
      }
    }
    
    def alreadyExplored(t : (Unique, ActorCell, Envelope)) : Boolean =
      alreadyExploredImpl(t._1.id)

    
    
    def convergent : (((Unique, ActorCell, Envelope)) => Boolean) = 
      return inUpcomingEvents(getNextTrace.drop(1).toSet, 
                              _: (Unique, ActorCell, Envelope))
                              
    def divergent : (((Unique, ActorCell, Envelope)) => Boolean) = 
      return !alreadyExplored(_: (Unique, ActorCell, Envelope))

  }

  
  def canBeScheduled : (((Unique, ActorCell, Envelope)) => Boolean) = {
    
    def isNotInSet(
        set : scala.collection.immutable.Set[Unique],
        t : (Unique, ActorCell, Envelope)) : Boolean = {
      return !(set.map { x => x.id } contains t._1.id)
    }
    
    def can(
        set : scala.collection.immutable.Set[Unique],
        t : (Unique, ActorCell, Envelope)) : Boolean = {
      
      var ret = isNotInSet(set, t)
      exploredStack.get(currentTrace.size - 1) match {
        case Some((set, branchSet)) =>
          val inExplored = branchSet.contains(t._1.id)
          ret = ret && !inExplored
        case None =>
      }
      
      return ret
    }
    
    return can(getNextTrace.drop(1).toSet, 
               _: (Unique, ActorCell, Envelope))
  }
  
  
  // When executing a trace, find the next trace event.
  def mutableTraceIterator( trace: Queue[Unique]) : Option[Unique] =
  trace.isEmpty match {
    case true => return None
    case _ =>
      val ret = trace.head
      return Some(ret)
  }
  
  
  // Get next message event from the trace.
  def getNextTraceMessage() : Option[Unique] = 
  mutableTraceIterator(nextTrace) match {
    // All spawn events are ignored.
    case some @ Some(Unique(s: SpawnEvent, id)) =>
      nextTrace.dequeue()
      getNextTraceMessage()
    // All system messages need to ignored.
    case some @ Some(Unique(t, 0)) => 
      nextTrace.dequeue()
      getNextTraceMessage()
    case some @ Some(Unique(t, id)) => 
      return some
    case None => return None
    case _ => throw new Exception("internal error")
  }
  
  
  def addEvent(event: Unique) = {
    currentTrace += event
  }
  
  
  def setExplored(index: Int, pair: (Unique, Unique)) =
  exploredStack.get(index) match {
    case Some((set, branches)) => set += pair
    case None =>
      val newElem = new HashSet[(Unique, Unique)] + pair
      exploredStack(index) = (newElem, new HashSet[Int])
  }
  
  
  def isExplored(pair: (Unique, Unique), seq: Queue[Unique]): Boolean = {

    for ((index, (set, branches)) <- exploredStack) set.contains(pair) match {
      case true => return true
      case false =>
    }
    
    return false
  }
  
  
  def aboutToPlay(seq: Queue[Unique]) {
    val nextTrace : Vector[Int] = seq.map { x => x.id }.toVector
    assert(!(exploredSeq contains nextTrace))
    exploredSeq += nextTrace
  }
  
  
  def dequeueNextTraceMessage() {
    nextTrace.dequeue()
  }
  
  
  def setNextTrace(trace: Queue[Unique]) = {
    nextTrace ++= trace
    prevTrace = currentTrace
    currentTrace = new Queue[Unique]
  }
  
  
  def getNextTrace() : Queue[Unique] = {
    return nextTrace.clone()
  }
  
  
  def getCurrentTrace() : Queue[Unique] = {
    return currentTrace.clone()
  }
  
  
  def startNewTrace() = {
    def size = currentTrace.zip(prevTrace).takeWhile(Function.tupled(_ == _)).size
    if (prevTrace.size != 0 && prevTrace != currentTrace)
      trimExplored(size)
    nextTrace.clear()
  }
  
  
  def rollback() {
    currentTrace = prevTrace.clone()
  }
  
  
  def trimExplored(index: Int) = {
    val cutIndex = index - 1
    val valIndex = index
    val branchID = List(prevTrace(valIndex).id)
    
    println("Trimming at cutIndex " + cutIndex + " valIndex " + valIndex + " branchID " + branchID)
    
    exploredStack.get(cutIndex) match {
      case Some((set, branchSet)) =>
        branchSet ++= branchID
      case None => 
        val tup @ (set, branchSet) = 
          (new HashSet[(Unique, Unique)], new HashSet[Int])
        branchSet ++= branchID
        exploredStack(cutIndex) = tup  
    }
    
    exploredStack = exploredStack.filterNot { other => other._1 > cutIndex }
    if (exploredStack.size > 0) {
      assert(cutIndex == exploredStack.map(x => x._1).max)
    }
  }
 
  
  def printExplored() = {
    for ((index, (set, branches)) <- exploredStack.toList.sortBy(t => (t._1))) {
      println(index + ": " + set.size)
    }
  }
  
  

  def progress() : Boolean = {
    return currentTrace != prevTrace
  }

  def clear() = {
    exploredStack.clear()
  }
}
