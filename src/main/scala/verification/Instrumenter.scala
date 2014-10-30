package akka.dispatch.verification

import akka.actor.ActorCell
import akka.actor.ActorRef
import akka.actor.Actor
import akka.dispatch.Envelope
import akka.dispatch.MessageQueue
import akka.dispatch.MessageDispatcher

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Queue
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet


class Instrumenter {
  
  type allowedT = HashSet[(ActorCell, Envelope)]
  type activeT = HashMap[ActorRef, Queue[(ActorCell, Envelope)]]
  type dispacthersT = HashMap[ActorRef, MessageDispatcher]

  val allowedMsgs = new allowedT
  val active = new activeT
  val dispatchers = new dispacthersT
  
  
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
    
    active.headOption match {
      case Some((receiver, queue)) =>
        
        if (queue.isEmpty == true) {
          active.remove(receiver) match {
            case Some(key) => "Removed the last element in the queue..."
            case None => throw new Exception("internal error")
          }
          schedule_new_message()
        } else {
          val (new_cell, envelope) = queue.dequeue()
          dispatch_new_message(new_cell, envelope)
        }

      case None => println("no more elements")
    }
  }
  
  

  def dispatch_new_message(cell: ActorCell, envelope: Envelope) = {
    val src = envelope.sender.path.name
    val dst = cell.self.path.name
    
    println("scheduling: " + src + " -> " + dst)

    val value: (ActorCell, Envelope) = (cell, envelope)
    allowedMsgs += value
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
    
    if (allowedMsgs contains value) {
      allowedMsgs.remove(value) match {
        case true => return true
        case false => throw new Exception("internal error")
      }
    }
    
    dispatchers(receiver) = dispatcher
    val msgs = active.getOrElse(receiver, new Queue[(ActorCell, Envelope)])
    active(receiver) = msgs += ((cell, envelope))
    println(Console.BLUE + "anqueue: " + src + " -> " + dst + Console.RESET);    

    return false
  }

}

