package akka.dispatch.verification

import akka.actor.ActorCell
import akka.actor.ActorRef
import akka.actor.Actor
import akka.dispatch.Envelope
import akka.dispatch.MessageQueue
import akka.dispatch.MessageDispatcher

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Queue
import scala.collection.mutable.Stack
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet


class Instrumenter {
  
  type allowedT = HashSet[(ActorCell, Envelope)]
  type pendingEventsT = HashMap[ActorRef, Queue[(ActorCell, Envelope)]]
  type dispacthersT = HashMap[ActorRef, MessageDispatcher]
  
  type finishedEventsT = Stack[(ActorCell, Envelope)]

  val dispatchers = new dispacthersT
  
  val allowedEvents = new allowedT
  val pendingEvents = new pendingEventsT  
  val finishedEvents = new finishedEventsT
  val seenActors = new HashSet[ActorRef]
  
  def new_actor(actor: ActorRef) = {
    println("System has created a new actor: " + actor.path.name)
    seenActors += actor
  }
  
  
  def trace_finished() = {
    println("Done executing the trace.")
  }
  
  
  def reply_prefix(prefix: dispacthersT) = {
  }
  
  
  
  def beginMessageReceive(cell: ActorCell) {
    println(Console.GREEN 
        + " ↓↓↓↓↓↓↓↓↓ " + cell.self.path.name + " ↓↓↓↓↓↓↓↓↓ " + 
        Console.RESET)
  }
  

  def afterMessageReceive(cell: ActorCell) {
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

      case None => trace_finished()
    }
  }
  
  

  def dispatch_new_message(cell: ActorCell, envelope: Envelope) = {
    val src = envelope.sender.path.name
    val dst = cell.self.path.name
    
    val value: (ActorCell, Envelope) = (cell, envelope)
    finishedEvents.push(value)
    println("#" + finishedEvents.length + " scheduling: " + src + " -> " + dst)

    allowedEvents += value
    dispatchers.get(cell.self) match {
      case Some(dispatcher) => dispatcher.dispatch(cell, envelope)
      case None => throw new Exception("internal error")
    }
  }  
  
  
  
  def aroundDispatch(dispatcher: MessageDispatcher, cell: ActorCell, 
      envelope: Envelope): Boolean = {
    
    val value: (ActorCell, Envelope) = (cell, envelope)
    val receiver = cell.self
    val src = envelope.sender.path.name
    val dst = receiver.path.name
    
    if(src == "deadLetters" | src == "$a") {
      println("allowing default "  + src + " -> " + dst)
      return true  
    }
    
    if (allowedEvents contains value) {
      allowedEvents.remove(value) match {
        case true => 
          return true
        case false => throw new Exception("internal error")
      }
    }
    
    dispatchers(receiver) = dispatcher
    val msgs = pendingEvents.getOrElse(receiver, new Queue[(ActorCell, Envelope)])
    pendingEvents(receiver) = msgs += ((cell, envelope))
    println(Console.BLUE + "anqueue: " + src + " -> " + dst + Console.RESET);    

    return false
  }

}

