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

class DPOR {

  val allowedMsgs = new HashSet[(ActorCell, Envelope)]
  val active = new HashMap[ActorRef, Queue[(ActorCell, Envelope)]]
  val dispatchers = new HashMap[ActorRef, MessageDispatcher]

  var counter : Integer = 0
  
  
  def beginMessageReceive(cell: ActorCell) {
    println(" ↓↓↓↓↓↓↓↓↓ " + cell.self.path.name + " ↓↓↓↓↓↓↓↓↓ ")
  }
  
  
  

  def afterMessageReceive(cell: ActorCell) {

    println(" ↑↑↑↑↑↑↑↑↑ " + cell.self.path.name + " ↑↑↑↑↑↑↑↑↑ ")
    schedule_new_message()

  }
  
  
  def schedule_new_message() : Unit = {
    
    active.headOption match {
      case Some((receiver, queue)) =>
        
        if (queue.isEmpty == false) {
          val (new_cell, envelope) = queue.dequeue()
          
          fire(new_cell, envelope)
        } else {
          active.remove(receiver) match {
            case Some(key) => "Removed the last element in the queue..."
            case None => throw new Exception("the queue should be there")
          }
          schedule_new_message()
        }

      case None => println("No more elements!")
    }
  }
  
  

  def fire(cell: ActorCell, envelope: Envelope) = {
    val src = envelope.sender.path.name
    val dst = cell.self.path.name
    
    println("scheduling: " + src + " -> " + dst)

    val value: (ActorCell, Envelope) = (cell, envelope)
    allowedMsgs += value
    dispatchers.get(cell.self) match {
      case Some(dispatcher) => dispatcher.dispatch(cell, envelope)
      case None => println("Not suppose to happen!")
    }
  }
  
  
  
  def aroundDispatch(dispatcher: MessageDispatcher, cell: ActorCell, 
      envelope: Envelope): Boolean = {
    
    val receiver = cell.self
    
    val value: (ActorCell, Envelope) = (cell, envelope)    
    counter = counter + 1
    
    val src = envelope.sender.path.name
    val dst = receiver.path.name
    
    if(src == "deadLetters" | src == "$a") {
      println("Allowing default "  + src + " -> " + dst)
      return true  
    }
    
    if (allowedMsgs contains value) {
      return true 
    }
    
    dispatchers(receiver) = dispatcher
    var msgs = active.getOrElse(receiver, new Queue[(ActorCell, Envelope)])
    msgs.enqueue( (cell, envelope) )
    active(receiver) = msgs
    println("anqueue: " + src + " -> " + dst);    

    return false
  }
  

  def aroundEnqueue(queue: MessageQueue, receiver: ActorRef, envelope: Envelope): Boolean = {
    return true
  }
  

  def afterEnqueue(queue: MessageQueue, receiver: ActorRef, handle: Envelope) {
  }

}

