package akka.dispatch.verification

import akka.actor.ActorCell
import akka.actor.ActorRef
import akka.actor.Actor
import akka.dispatch.Envelope
import akka.dispatch.MessageQueue
import akka.dispatch.Dispatcher

import scala.collection.concurrent.TrieMap


class DPOR {
  
  val queued = new TrieMap[ActorRef, Any]

  def beginMessageReceive(cell: ActorCell) {
    println(cell.self.path.name + " receiveMessage")
  }

  def afterMessageReceive(cell: ActorCell) {
    println(cell.self.path.name + " receiveMessage (end)")
  }

  def beforeEnqueue(queue: MessageQueue, receiver: ActorRef, handle: Envelope) {

    queued += (receiver -> true)
    
    var message = "MessageQueue:enqueue("
    message ++= handle.sender.path.name + " -> "
    message ++= receiver.path.name + ") Remainning: "

    println(message)
  }

  def afterEnqueue(queue: MessageQueue, receiver: ActorRef, handle: Envelope) {
  }

}

