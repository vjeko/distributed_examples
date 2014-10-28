package akka.dispatch.verification

import akka.actor.ActorCell
import akka.actor.ActorRef
import akka.actor.Actor
import akka.dispatch.Envelope
import akka.dispatch.MessageQueue
import akka.dispatch.Dispatcher

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Queue
import scala.collection.mutable.HashMap
import scala.collection.mutable.Set



class DPOR {
  
  val active = new HashMap[ ActorRef, Queue[Envelope] ]

  def beginMessageReceive(cell: ActorCell) {
    println(cell.self.path.name + " receiveMessage")
  }

  def afterMessageReceive(cell: ActorCell) {
    
    println(cell.self.path.name + ": receiveMessage (end)")
    println(cell.self.path.name + ": dequeing a new message...")
    
    active.headOption match {
      case Some((actor, queue)) =>
        queue.headOption match {
          case Some(envelope) =>
            fire(actor, envelope)
          case None => println("No more elements!")
        }
        
      case None => println("No more elements!")
    }

  }

  def fire(receiver: ActorRef, envelope: Envelope) = {
    
  }

  
  def aroundEnqueue(receiver: ActorRef, envelope: Envelope) : Boolean = {
    
    val src = envelope.sender.path.name
    val dst = receiver.path.name
    
    if(src == "$a") {
      return true
    }
    
    var msgs = active.getOrElse(receiver, new Queue[Envelope] )
    msgs.enqueue(envelope)
    println("enqueing a message from " 
        + envelope.sender.path.name + " -> " 
        + receiver.path.name)
    
    return true
  }

  
  def afterEnqueue(queue: MessageQueue, receiver: ActorRef, handle: Envelope) {
  }

}

