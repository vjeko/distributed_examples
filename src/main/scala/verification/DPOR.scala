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
    println(cell.self.path.name + " receiveMessage")
  }
  
  
  

  def afterMessageReceive(cell: ActorCell) {

    println(cell.self.path.name + ": receiveMessage (end) " + active.size)

    active.headOption match {
      case Some((receiver, queue)) =>
        
        if (queue.isEmpty == false) {
          
          println(cell.self.path.name + ": dequeing a new message...")
          val (new_cell, envelope) = queue.dequeue()
          fire(new_cell, envelope)
        } else {
          println("No more elements in the queue!")
          active.remove(receiver) match {
            case Some(key) => "Removed the last element in the queue..."
            case _ => "ERROR: Element does not exist!"
          }
          afterMessageReceive(cell)
        }

      case None => println("No more elements!")
    }
  }
  
  

  def fire(cell: ActorCell, envelope: Envelope) = {
    
    val value: (ActorCell, Envelope) = (cell, envelope)
    allowedMsgs += value
    dispatchers.get(cell.self) match {
      case Some(dispatcher) => dispatcher.dispatch(cell, envelope)
      case None => println("Not suppose to happen!")
    }
  }
  
  
  
  def aroundDispatch(dispatcher: MessageDispatcher, cell: ActorCell, 
      envelope: Envelope): Boolean = {
    
    println("aroundDispatch")
    
    val receiver = cell.self
    
    val value: (ActorCell, Envelope) = (cell, envelope)    
    counter = counter + 1
    
    val src = envelope.sender.path.name
    val dst = receiver.path.name
    
    println("enqueing a message from "
      + envelope.sender.path.name + " -> "
      + receiver.path.name);

    if(src == "deadLetters" | src == "$a") {
      println("Allowing default...")
      return true  
    }
    
    if (allowedMsgs contains value) {
      println("Should we allow this message? " + (allowedMsgs contains value))
      return true 
    }
    
    dispatchers(receiver) = dispatcher
    var msgs = active.getOrElse(receiver, new Queue[(ActorCell, Envelope)])
    msgs.enqueue( (cell, envelope) )
    active(receiver) = msgs

    return false
  }
  

  def aroundEnqueue(queue: MessageQueue, receiver: ActorRef, envelope: Envelope): Boolean = {
    return true
  }
  

  def afterEnqueue(queue: MessageQueue, receiver: ActorRef, handle: Envelope) {
  }

}

