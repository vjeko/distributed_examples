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
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

class Scheduler(_instrumenter : Instrumenter) {
  
  var intrumenter = _instrumenter 
  var currentTime = 0
  
  type CurrentTimeQueueT = Queue[Event]
  
  val currentlyProduced = new CurrentTimeQueueT
  val currentlyConsumed = new CurrentTimeQueueT
  
  val producedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  val consumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  
  val allowedEvents = new HashSet[(ActorCell, Envelope)]
  val pendingEvents = new HashMap[ActorRef, Queue[(ActorCell, Envelope)]]  
  
  def schedule_new_message() : Option[(ActorCell, Envelope)] = {
    
    pendingEvents.headOption match {
      
     case Some((receiver, queue)) =>
        if (queue.isEmpty == true) {
          pendingEvents.remove(receiver) match {
            case Some(key) => schedule_new_message()
            case None => throw new Exception("internal error")
          }
          
        } else {
          val (new_cell, envelope) = queue.dequeue()
          allowedEvents += ((new_cell, envelope) : (ActorCell, Envelope))
          return Some((new_cell, envelope))
        }

      case None => return None
    }
  }
  
  def event_consumed(cell: ActorCell, envelope: Envelope) = {
    val value: (ActorCell, Envelope) = (cell, envelope)
    val receiver = cell.self
    val snd = envelope.sender.path.name
    val rcv = receiver.path.name
    
    currentlyConsumed.enqueue(new MsgEvent(rcv, snd, envelope.message, cell, envelope))
  }
  
  
  
  def event_produced(cell: ActorCell, envelope: Envelope) = {
    val value: (ActorCell, Envelope) = (cell, envelope)
    val receiver = cell.self
    val snd = envelope.sender.path.name
    val rcv = receiver.path.name
    
    currentlyProduced.enqueue(new MsgEvent(rcv, snd, envelope.message, cell, envelope))
  }
  
  def event_produced(_parent: String,
    _props: Props, _name: String, _actor: ActorRef) = {
    
    currentlyProduced.enqueue(new SpawnEvent(_parent, _props, _name, _actor))
  }
  
  def after_receive(cell: ActorCell) {
    
    producedEvents.enqueue( (currentTime, currentlyProduced.clone()) )
    consumedEvents.enqueue( (currentTime, currentlyConsumed.clone()) )
    
    println("Produced: " + currentlyProduced.size + " Consumed: " + currentlyConsumed.size)
    
    currentlyProduced.clear()
    currentlyConsumed.clear()
    
    currentTime += 1
  }
  
  def trace_finished() = {
    currentTime = 0
  }
  
  def start_trace() : MsgEvent = {

    val (epoch, firstTick) = consumedEvents.headOption match {
      case Some(elem) => elem 
      case _ => throw new Exception("no previously consumed events")
      
    }
    
    val firstEvent = firstTick.headOption match {
      case Some(elem : MsgEvent) => elem 
      case _ => throw new Exception("first event not a message")
    }
    
    producedEvents.clear()
    consumedEvents.clear()
    
    return firstEvent
    
  }

}