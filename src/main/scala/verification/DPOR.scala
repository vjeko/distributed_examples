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

  val allowedMsgs = new HashSet[(ActorRef, Envelope)]
  val active = new HashMap[ActorRef, Queue[Envelope]]
  val queues = new HashMap[ActorRef, MessageQueue]

  var counter : Integer = 0
  
  
  def beginMessageReceive(cell: ActorCell) {
    println(cell.self.path.name + " receiveMessage")
  }
  
  

  def afterMessageReceive(cell: ActorCell) {

    println(cell.self.path.name + ": receiveMessage (end) " + active.size)

    val value: (ActorRef, Envelope) = (cell.self, cell.currentMessage)
    
    active.headOption match {
      case Some((receiver, queue)) =>
        queue.headOption match {
          case Some(envelope) => 
            println(cell.self.path.name + ": dequeing a new message...")
            fire(receiver, envelope)
          case None => println("No more elements!")
        }

      case None => println("No more elements!")
    }
  }
  
  

  def fire(receiver: ActorRef, envelope: Envelope) = {
    
    val value: (ActorRef, Envelope) = (receiver, envelope)
    allowedMsgs += value
    queues.get(receiver) match {
      case Some(queue) => queue.enqueue(receiver, envelope)
      case None => println("Not suppose to happen!")
    }
  }
  
  def aroundDispatch(
      dispatcher: MessageDispatcher, receiver: ActorCell, envelope: Envelope): Boolean = {
    println("aroundDispatch")
    return true;
  }

  def aroundEnqueue(queue: MessageQueue, receiver: ActorRef, envelope: Envelope): Boolean = {

    val value: (ActorRef, Envelope) = (receiver, envelope)    
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
    
    queues(receiver) = queue
    var msgs = active.getOrElse(receiver, new Queue[Envelope])
    msgs.enqueue(envelope)
    active(receiver) = msgs

    return false
  }

  def afterEnqueue(queue: MessageQueue, receiver: ActorRef, handle: Envelope) {
  }

}

