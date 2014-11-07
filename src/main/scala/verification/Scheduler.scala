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
  
  var producedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  var consumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  
  var prevProducedEvents : Queue[ (Integer, CurrentTimeQueueT) ] = null
  var prevConsumedEvents : Queue[ (Integer, CurrentTimeQueueT) ] = null
  
  val pendingEvents = new HashMap[ActorRef, Queue[(ActorCell, Envelope)]]  
  
  def get_next_message(queue_old: Queue[Event],
                       queue_new: Queue[(ActorCell, Envelope)]) : Unit = {
    
    val head = queue_new.headOption match {
      case Some((a, b)) => a
      case None => throw new Exception("internal error")
    }
    
    println(head.self.path.name)
    
    var p = ""
    
    for(event <- queue_old) {
      event match {
        case e : MsgEvent => 
          p += " " + e.receiver
          if (e.receiver == head.self.path.name) println(true)
        case _ => 
      }
    }
    
    println(p)
    
  }
  
  def schedule_new_message() : Option[(ActorCell, Envelope)] = {
    
    pendingEvents.headOption match {
      
      case Some((receiver, queue)) =>
         if (queue.isEmpty == true) {
           pendingEvents.remove(receiver) match {
             case Some(key) =>
                
               if (prevConsumedEvents != null) {
                 println("DEQUEUE " + prevConsumedEvents.size)
                 if (!prevConsumedEvents.isEmpty) prevConsumedEvents.dequeue()
               }
                 
                 
               schedule_new_message()
             case None => throw new Exception("internal error")
           }
           
         } else {

           if (prevConsumedEvents != null) {
             
             
             prevConsumedEvents.headOption match {
               case Some((clock, qq)) =>
                println(clock + " " + qq.size)
                if (!prevConsumedEvents.isEmpty) prevConsumedEvents.dequeue()
                get_next_message(qq, queue)
                case None =>
             }
             
           }
           val (new_cell, envelope) = queue.dequeue()
           return Some((new_cell, envelope))

         }
      
      case None => return None
    }
  }
  
  def event_consumed(event: Event) = {
    currentlyConsumed.enqueue(event)
  }
  
  
  
  def event_consumed(cell: ActorCell, envelope: Envelope) = {
    val value: (ActorCell, Envelope) = (cell, envelope)
    val receiver = cell.self
    val snd = envelope.sender.path.name
    val rcv = receiver.path.name
    
    currentlyConsumed.enqueue(new MsgEvent(rcv, snd, envelope.message, cell, envelope))
  }
  
  
  
  def event_produced(event: Event) = {
    currentlyProduced.enqueue(event)
  }
  
  def event_produced(cell: ActorCell, envelope: Envelope) = {
    val value: (ActorCell, Envelope) = (cell, envelope)
    val receiver = cell.self
    val snd = envelope.sender.path.name
    val rcv = receiver.path.name
    
    val msgs = pendingEvents.getOrElse(receiver, new Queue[(ActorCell, Envelope)])
    pendingEvents(receiver) = msgs += ((cell, envelope))
    
    currentlyProduced.enqueue(new MsgEvent(rcv, snd, envelope.message, cell, envelope))
  }
  
  
  def event_produced(_parent: String,
    _props: Props, _name: String, _actor: ActorRef) = {
    
    currentlyProduced.enqueue(new SpawnEvent(_parent, _props, _name, _actor))
  }
  
  def before_receive(cell: ActorCell) {
    
    producedEvents.enqueue( (currentTime, currentlyProduced.clone()) )
    consumedEvents.enqueue( (currentTime, currentlyConsumed.clone()) )
        
    currentlyProduced.clear()
    currentlyConsumed.clear()
    
    currentTime += 1
  }
  
  
  def after_receive(cell: ActorCell) {
  }
  
  def trace_finished() = {
    currentTime = 0
  }
  
  def next_event() : Event = {
    
    val (epoch, firstTick) = prevConsumedEvents.headOption match {
      case Some(elem) => elem 
      case _ => throw new Exception("no previously consumed events")
      
    }
    
    val firstEvent = firstTick.isEmpty match {
      case false => firstTick.dequeue()
      case true => throw new Exception("first event not a message")
    }
    
    return firstEvent
  }
  
  
  def start_trace() : Unit = {

    prevConsumedEvents = consumedEvents
    prevProducedEvents = producedEvents
    
    producedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
    consumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
    
  }

}