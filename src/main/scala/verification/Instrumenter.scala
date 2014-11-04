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

class Instrumenter {

  val dispatchers = new HashMap[ActorRef, MessageDispatcher]
  
  val allowedEvents = new HashSet[(ActorCell, Envelope)]
  val pendingEvents = new HashMap[ActorRef, Queue[(ActorCell, Envelope)]]  
  val finishedEvents = new Queue[(String, String, Any, ActorCell, Envelope)]
  
  val seenActors = new HashSet[(ActorSystem, Any)]
  val actorMappings = new HashMap[String, ActorRef]
  val actorNames = new HashSet[String]
  
  var counter = 0   
  var started = false;
  
  
  def new_actor(system: ActorSystem, 
      props: Props, name: String, actor: ActorRef) = {
    
    println("System has created a new actor: " + actor.path.name)
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
  
  
  def new_syatem() {
    
    println("Starting the actors.")
    val newSystem = ActorSystem("new-system-" + counter)
    counter += 1

    val newSeenActors = seenActors.clone()
    seenActors.clear()
    
    for ((systemx, args) <- newSeenActors) {
      args match {
        case (actor: ActorRef, props: Props, name: String) =>
          println("starting " + name)
          newSystem.actorOf(props, name)
      }
    }
    
    val (rcv, snd, msg, cell, envelope) = finishedEvents.dequeue()
    
    finishedEvents.clear()
    actorMappings.get(rcv) match {
      
      case Some(ref) =>
        ref ! msg
        println("Found it!")
        //dispatchers(cell.self) = newSystem.dispatchers.defaultGlobalDispatcher
        //dispatch_new_message(cell, envelope)
      case None => throw new Exception("internal error")
    }
    

  }
  
  
  
  def trace_finished() = {
    println("Done executing the trace.")
    started = false
    
    val loop = new Breaks;
    loop.breakable {
          
      println("Stopping the actors.")
      for ((system, args) <- seenActors) {
        system.shutdown()
        system.registerOnTermination(new_syatem())
        loop.break
      }
      
    }
    
  }
  
  
  def beginMessageReceive(cell: ActorCell) {
    
    println("beginMessageReceive")
    if (isSystemMessage(cell.sender.path.name, cell.self.path.name)) return
    println(Console.GREEN 
        + " ↓↓↓↓↓↓↓↓↓ " + cell.self.path.name + " ↓↓↓↓↓↓↓↓↓ " + 
        Console.RESET)
  }
  

  def afterMessageReceive(cell: ActorCell) {
    if (isSystemMessage(cell.sender.path.name, cell.self.path.name)) return
    println(Console.RED 
        + " ↑↑↑↑↑↑↑↑↑ " + cell.self.path.name + " ↑↑↑↑↑↑↑↑↑ " 
        + Console.RESET)
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
    finishedEvents.enqueue( 
        ((rcv, snd, envelope.message, cell, envelope) 
            :(String, String, Any, ActorCell, Envelope)) )
    println("#" + finishedEvents.length + " scheduling: " + snd + " -> " + rcv)

    dispatchers.get(cell.self) match {
      case Some(dispatcher) => dispatcher.dispatch(cell, envelope)
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
    val src = envelope.sender.path.name
    val dst = receiver.path.name
    
    if (isSystemMessage(src, dst)) { return true }
    
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
    println(Console.BLUE + "anqueue: " + src + " -> " + dst + Console.RESET);    
    
    return false
  }

}

