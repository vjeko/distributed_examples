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
import scala.collection.Iterator

class Scheduler(_instrumenter : Instrumenter) {
  
  var intrumenter = _instrumenter 
  var currentTime = 0
  var index = 0
  
  type CurrentTimeQueueT = Queue[Event]
  
  val currentlyProduced = new CurrentTimeQueueT
  val currentlyConsumed = new CurrentTimeQueueT
  
  var producedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  var consumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  
  var prevProducedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  var prevConsumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
  
  val pendingEvents = new HashMap[ActorRef, Queue[(ActorCell, Envelope)]]  
  
  
  
  def get_next_message(queue_new: Queue[(ActorCell, Envelope)]) : Option[MsgEvent] = {

    def get_message() : Option[MsgEvent] = { 
      seq() match {
        case Some(v : MsgEvent) => 
          println("MSgEvent")
          Some(v)
        case Some(v : Event) => 
          println("Event")
          get_message()
        case None => None
      }
    }
   
    return get_message()
    
    
  }
  
  
  
  def schedule_new_message() : Option[(ActorCell, Envelope)] = {
    
    
    def extract(e: MsgEvent, c: (ActorCell, Envelope)) : Boolean = {
      val (cell, env) = c
      println (e.receiver + " == " + cell.self.path.name)
      e.receiver == cell.self.path
    }

    pendingEvents.headOption match {
      
      case Some((receiver, queue)) =>
         if (queue.isEmpty == true) {
           
           pendingEvents.remove(receiver) match {
             case Some(key) => schedule_new_message()
             case None => throw new Exception("internal error")
           }
           
         } else {

           val maybe = get_next_message(queue) match {
             case Some(msg_event) => 
               println(queue.size)
               queue.dequeueFirst(extract(msg_event, _))
             case None => None
           }

           maybe match {
             case Some(xxx) =>
               println("some")
               return maybe
             case None => 
               val (new_cell, envelope) = queue.dequeue() 
               println("Is -> " + new_cell.self.path.name)
               return Some((new_cell, envelope))
           }

         }
      
      case None => return None
    }
  }
  
  def event_consumed(event: Event) = {
    currentlyConsumed.enqueue(event)
  }
  
  
  def seq() : Option[Event] = {
    
    println(prevConsumedEvents.size)
    if(prevConsumedEvents.isEmpty)
      return None
      
    val (count, q) = prevConsumedEvents.head
    q.isEmpty match {
      case true =>
        println("q is empty")
        prevConsumedEvents.dequeue()
        seq()
      case false =>
        println("q is not empty")
        val ret = Some(q.dequeue())
        return ret
    }
    

  }
  
  
  def event_consumed(cell: ActorCell, envelope: Envelope) = {
    val value: (ActorCell, Envelope) = (cell, envelope)
    val receiver = cell.self
    val snd = envelope.sender.path.name
    val rcv = receiver.path.name
    
    currentlyConsumed.enqueue(new MsgEvent(snd, rcv, envelope.message, cell, envelope))
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
    
    currentlyProduced.enqueue(new MsgEvent(snd, rcv, envelope.message, cell, envelope))
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
    
    seq() match {
      case Some(v) => v
      case None => throw new Exception("no previously consumed events")
    }
    
  }
  
  
  def start_trace() : Unit = {

    prevConsumedEvents = consumedEvents
    prevProducedEvents = producedEvents
    
    producedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
    consumedEvents = new Queue[ (Integer, CurrentTimeQueueT) ]
    
    prevConsumedEvents.iterator
  }

}