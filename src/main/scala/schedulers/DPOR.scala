package akka.dispatch.verification

import akka.actor.ActorCell
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.PoisonPill
import akka.actor.Props
import akka.dispatch.Envelope
import akka.dispatch.MessageQueue
import akka.dispatch.MessageDispatcher

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Queue
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.Iterator

import scalax.collection.mutable.Graph 
import scalax.collection.GraphPredef._, scalax.collection.GraphEdge._
import scalax.collection.edge.LDiEdge     // labeled directed edge
import scalax.collection.edge.Implicits._ // shortcuts

import scalax.collection.io.dot._
import scalax.collection.edge.LDiEdge,
       scalax.collection.edge.Implicits._
// A basic scheduler
class DPOR extends Scheduler {
  
  var instrumenter = Instrumenter
  var currentTime = 0
  var index = 0
  
  type CurrentTimeQueueT = Queue[Event]
  
  var currentlyProduced = new CurrentTimeQueueT
  var currentlyConsumed = new CurrentTimeQueueT
  
  var producedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  var consumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  
  var prevProducedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  var prevConsumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
 
  var parentEvent : Event = null
  
  // Current set of enabled events.
  val pendingEvents = new HashMap[String, Queue[(Event, ActorCell, Envelope)]]  
  val actorNames = new HashSet[String]
 
  
  val g = Graph[Event, DiEdge]()
  
  
  
  def isSystemCommunication(sender: ActorRef, receiver: ActorRef): Boolean = {
    if (sender == null || receiver == null) return true
    return isSystemMessage(sender.path.name, receiver.path.name)
  }
  
  // Is this message a system message
  def isSystemMessage(sender: String, receiver: String): Boolean = {
    if ((actorNames contains sender) || (actorNames contains receiver))
      return false
    
    return true
  }
  
  
  // Notification that the system has been reset
  def start_trace() : Unit = {
    prevProducedEvents = producedEvents
    prevConsumedEvents = consumedEvents
    producedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
    consumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  }
  
  
  // When executing a trace, find the next trace event.
  private[this] def mutable_trace_iterator(
      trace: Queue[ (Integer, CurrentTimeQueueT) ]) : Option[Event] = { 
    
    if(trace.isEmpty) return None
      
    val (count, q) = trace.head
    q.isEmpty match {
      case true =>
        trace.dequeue()
        mutable_trace_iterator(trace)
      case false => return Some(q.dequeue())
    }
  }
  
  

  // Get next message event from the trace.
  private[this] def get_next_trace_message() : Option[MsgEvent] = {
    mutable_trace_iterator(prevConsumedEvents) match {
      case Some(v : MsgEvent) =>  Some(v)
      case Some(v : Event) => get_next_trace_message()
      case None => None
    }
  }
  
  
  
  // Figure out what is the next message to schedule.
  def schedule_new_message() : Option[(ActorCell, Envelope)] = {
  
    // Filter for messages belong to a particular actor.
    def is_the_same(e: MsgEvent, c: (Event, ActorCell, Envelope)) : Boolean = {
      val (event, cell, env) = c
      e.receiver == cell.self.path.name
    }

    // Get from the current set of pending events.
    def get_pending_event()  : Option[(Event, ActorCell, Envelope)] = {
      // Do we have some pending events
      pendingEvents.headOption match {
        case Some((receiver, queue)) =>
           if (queue.isEmpty == true) {
             
             pendingEvents.remove(receiver) match {
               case Some(key) => get_pending_event()
               case None => throw new Exception("internal error")
             }
             
           } else {
              Some(queue.dequeue())
           }
        case None =>
          //instrumenter().restart_system()
          None
      }
    }

    val result = get_next_trace_message() match {
      // The trace says there is something to run.
      case Some(msg_event: MsgEvent) =>
        pendingEvents.get(msg_event.receiver) match {
          case Some(queue) => queue.dequeueFirst(is_the_same(msg_event, _))
          case None => None
        }
      // The trace says there is nothing to run so we have either exhausted our
      // trace or are running for the first time. Use any enabled transitions.
      case None => get_pending_event()
    }
    
    result match {
      case Some((ev, c, env)) =>
        
        g.find(ev) match {
          case Some(xxxx) =>
            if(parentEvent != null) {
              g.addEdge(parentEvent, xxxx)(DiEdge)
            }
            parentEvent = ev
          case None => 
            
        }
        

        Some((c, env))
      case _ => None
    }
    
    
  }
  
  
  // Get next event
  def next_event() : Event = {
    mutable_trace_iterator(prevConsumedEvents) match {
      case Some(v) => v
      case None => throw new Exception("no previously consumed events")
    }
  }
  

  // Record that an event was consumed
  def event_consumed(event: Event) = {
    currentlyConsumed.enqueue(event)
  }
  
  
  def event_consumed(cell: ActorCell, envelope: Envelope) = {
    currentlyConsumed.enqueue(new MsgEvent(
        envelope.sender.path.name, cell.self.path.name, 
        envelope.message))
  }
  
  
  // Record that an event was produced 
  def event_produced(event: Event) = {
    g.add(event)
    
    println("Produced a spawn.")
    event match {
      case event : SpawnEvent => actorNames += event.name
    }
    currentlyProduced.enqueue(event)
  }
  
  
  def event_produced(cell: ActorCell, envelope: Envelope) = {
    val snd = envelope.sender.path.name
    val rcv = cell.self.path.name
    val msgs = pendingEvents.getOrElse(rcv, new Queue[(Event, ActorCell, Envelope)])
    
    println("Produced a message.")
    val event = new MsgEvent(snd, rcv, envelope.message)
    
    pendingEvents(rcv) = msgs += ((event, cell, envelope))
    
    g.add(event)
    currentlyProduced.enqueue(event)
  }
  
  
  // Called before we start processing a newly received event
  def before_receive(cell: ActorCell) {
    producedEvents.enqueue( (currentTime, currentlyProduced) )
    consumedEvents.enqueue( (currentTime, currentlyConsumed) )
    currentlyProduced = new CurrentTimeQueueT
    currentlyConsumed = new CurrentTimeQueueT
    currentTime += 1
    println(Console.GREEN 
        + " ↓↓↓↓↓↓↓↓↓ ⌚  " + currentTime + " | " + cell.self.path.name + " ↓↓↓↓↓↓↓↓↓ " + 
        Console.RESET)
  }
  
  
  // Called after receive is done being processed 
  def after_receive(cell: ActorCell) {
    println(Console.RED 
        + " ↑↑↑↑↑↑↑↑↑ ⌚  " + currentTime + " | " + cell.self.path.name + " ↑↑↑↑↑↑↑↑↑ " 
        + Console.RESET)
        
  }
  
  def get_dot() {
    val root = DotRootGraph(
        directed = true,
        id = Some("DPOR"))

    def edgeTransformer(
        innerEdge: scalax.collection.Graph[Event, DiEdge]#EdgeT): 
        Option[(DotGraph, DotEdgeStmt)] = {
      
      val edge = innerEdge.edge

      val src = edge.from.value match {
        case x: MsgEvent => x.id.toString()
        case y => return None
      }

      val dst = edge.to.value match {
        case x: MsgEvent => x.id.toString()
        case _ => return None
      }
      
      //println (src + " -> " +  dst)

      return Some(root, DotEdgeStmt(src, dst, Nil))
    }
    
    val str = g.toDot(root, edgeTransformer)
    println(str)
  }

  
  
  def notify_quiescence() {
    
    get_dot()
    currentTime = 0
    println("Total " + consumedEvents.size + " events.")
  }
  

}
