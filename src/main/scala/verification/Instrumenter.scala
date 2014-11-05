package akka.dispatch.verification

import akka.actor.ActorCell
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.PoisonPill
import akka.actor.Props;

import akka.dispatch.Envelope
import akka.dispatch.MessageQueue
import akka.dispatch.MessageDispatcher

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Queue
import scala.collection.mutable.Stack
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.util.control.Breaks

class Event

class MsgEvent(_sender: String, _receiver: String, _msg: Any, 
               _cell: ActorCell, _envelope: Envelope) extends Event {
  
  val sender = _sender
  val receiver = _receiver
  val msg = _msg
  val cell = _cell
  val envelope = _envelope
}


class SpawnEvent(_parent: String,
    _props: Props, _name: String, _actor: ActorRef) extends Event {
  
  val parent = _parent
  val props = _props
  val name = _name
  val actor = _actor
}


class Instrumenter {

  val dispatchers = new HashMap[ActorRef, MessageDispatcher]
  
  val allowedEvents = new HashSet[(ActorCell, Envelope)]
  val pendingEvents = new HashMap[ActorRef, Queue[(ActorCell, Envelope)]]  
  val finishedEvents = new Queue[(String, String, Any, ActorCell, Envelope)]
  
  type messageT = (String, String, Any, ActorCell, Envelope)
  type CurrentTimeQueueT = Queue[Event]
  
  val currentlyProduced = new CurrentTimeQueueT
  val currentlyConsumed = new CurrentTimeQueueT
  
  val producedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  val consumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  
  val seenActors = new HashSet[(ActorSystem, Any)]
  val actorMappings = new HashMap[String, ActorRef]
  val actorNames = new HashSet[String]
  
  var currentActor = ""
  var currentTime = 0
  var counter = 0   
  var started = false;
  
  
  def new_actor(system: ActorSystem, 
      props: Props, name: String, actor: ActorRef) = {
    
    println("System has created a new actor: " + actor.path.name)
    currentlyProduced.enqueue(new SpawnEvent(currentActor, props, name, actor))
    
    if (!started) {
      seenActors += ((system, (actor, props, name)))
    }
    actorMappings(name) = actor
    actorNames += name
  }
  
  def new_actor(system: ActorSystem, 
      props: Props, actor: ActorRef) = {
    
    println("System has created a new actor: " + actor.path.name)
    if (started) {
      seenActors += ((system, (actor, props)))
    }
  }
  
  
  def restart_system(sys: ActorSystem, argQueue: Queue[Any]) {
    
    val newSystem = ActorSystem("new-system-" + counter)
    counter += 1
    println("Started a new actor system.")

    for (args <- argQueue) {
      args match {
        case (actor: ActorRef, props: Props, name: String) =>
          println("starting " + name)
          newSystem.actorOf(props, name)
      }
    }
    
    val (epoch, firstTick) = consumedEvents.headOption match {
      case Some(elem) => elem 
      case None => throw new Exception("no previously consumed events")
    }
    
    val firstEvent = firstTick.headOption match {
      case Some(elem : MsgEvent) => elem 
      case _ => throw new Exception("first event not a message")
    }
    
    producedEvents.clear()
    consumedEvents.clear()
    
    actorMappings.get(firstEvent.sender) match {

      case Some(ref) =>
        ref ! firstEvent.msg
        println("Found it!")
        //dispatchers(cell.self) = newSystem.dispatchers.defaultGlobalDispatcher
        //dispatch_new_message(cell, envelope)
      case None => throw new Exception("no such actor " + firstEvent.receiver)
    }
  }
  
  
  
  def trace_finished() = {
    println("Done executing the trace.")
    started = false
    currentTime = 0
        
    val allSystems = new HashMap[ActorSystem, Queue[Any]]
    for ((system, args) <- seenActors) {
      val argQueue = allSystems.getOrElse(system, new Queue[Any])
      argQueue.enqueue(args)
      allSystems(system) = argQueue
    }

    seenActors.clear()
    for ((system, argQueue) <- allSystems) {
        println("Shutting down the actor system. " + argQueue.size)
        system.shutdown()
        system.registerOnTermination(restart_system(system, argQueue))
    }
    
  }
  
  
  def beginMessageReceive(cell: ActorCell) {
    
    if (isSystemMessage(cell.sender.path.name, cell.self.path.name)) return

    currentActor = cell.self.path.name
    
    println(Console.GREEN 
        + " ↓↓↓↓↓↓↓↓↓ ⌚  " + currentTime + " | " + cell.self.path.name + " ↓↓↓↓↓↓↓↓↓ " + 
        Console.RESET)
  }
  

  def afterMessageReceive(cell: ActorCell) {
    if (isSystemMessage(cell.sender.path.name, cell.self.path.name)) return
    println(Console.RED 
        + " ↓↓↓↓↓↓↓↓↓ ⌚  " + currentTime + " | " + cell.self.path.name + " ↑↑↑↑↑↑↑↑↑ " 
        + Console.RESET)
    
    producedEvents.enqueue( (currentTime, currentlyProduced.clone()) )
    consumedEvents.enqueue( (currentTime, currentlyConsumed.clone()) )
    println("Produced: " + currentlyProduced.size + " Consumed: " + currentlyConsumed.size)
    
    currentlyProduced.clear()
    currentlyConsumed.clear()
    
    currentTime += 1
    schedule_new_message()
  }
  
  
  
  def schedule_new_message() : Unit = {
    
    pendingEvents.headOption match {
      
      case Some((receiver, queue)) =>
        if (queue.isEmpty == true) {
          pendingEvents.remove(receiver) match {
            case Some(key) => "Removed the last element in the queue..."
            case None => throw new Exception("internal error")
          }
          schedule_new_message()
        } else {
          val (new_cell, envelope) = queue.dequeue()
          dispatch_new_message(new_cell, envelope)
        }

      case None => 
        if(started && counter < 4) trace_finished()
    }
  }
  
  

  def dispatch_new_message(cell: ActorCell, envelope: Envelope) = {
    val snd = envelope.sender.path.name
    val rcv = cell.self.path.name
    
    allowedEvents += ((cell, envelope) : (ActorCell, Envelope))        

    println(" scheduling: " + snd + " -> " + rcv)

    dispatchers.get(cell.self) match {
      case Some(dispatcher) => 
        currentlyConsumed.enqueue(new MsgEvent(rcv, snd, envelope.message, cell, envelope))
        dispatcher.dispatch(cell, envelope)
      case None => throw new Exception("internal error")
    }
  }  
  
  
  def isSystemMessage(src: String, dst: String): Boolean = {

    if ((actorNames contains src) ||
        (actorNames contains dst)
    ) return false
    
    return true
  }
  
  
  def aroundDispatch(dispatcher: MessageDispatcher, cell: ActorCell, 
      envelope: Envelope): Boolean = {
    
    val value: (ActorCell, Envelope) = (cell, envelope)
    val receiver = cell.self
    val snd = envelope.sender.path.name
    val rcv = receiver.path.name
    
    if (isSystemMessage(snd, rcv)) { return true }
    
    if (allowedEvents contains value) {
      allowedEvents.remove(value) match {
        case true => 
          return true
        case false => throw new Exception("internal error")
      }
    }
    
    dispatchers(receiver) = dispatcher
    if (!started) {
      started = true
      dispatch_new_message(cell, envelope)
      return false
    }
    
    val msgs = pendingEvents.getOrElse(receiver, new Queue[(ActorCell, Envelope)])
    pendingEvents(receiver) = msgs += ((cell, envelope))
    currentlyProduced.enqueue(new MsgEvent(rcv, snd, envelope.message, cell, envelope))

    println(Console.BLUE + "enqueue: " + snd + " -> " + rcv + Console.RESET);

    return false
  }

}

