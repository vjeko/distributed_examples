package akka.dispatch.verification

import akka.actor.ActorCell
import akka.actor.ActorRef
import akka.actor.Actor
import akka.dispatch.Envelope

import akka.dispatch.MessageQueue
import akka.dispatch.Dispatcher

/**
 * Dedicates a unique thread for each actor passed in as reference. Served through its messageQueue.
 *
 * The preferred way of creating dispatchers is to define configuration of it and use the
 * the `lookup` method in [[akka.dispatch.Dispatchers]].
 */
class DPOR {

  def beginMessageReceive(cell: ActorCell) {
    println(cell.self.path.name + " receiveMessage")
  }

  def afterMessageReceive(cell: ActorCell) {
    println(cell.self.path.name + " receiveMessage (end)")
  }

  def beforeEnqueue(queue: MessageQueue, receiver: ActorRef, handle: Envelope) {
    
    var message = "MessageQueue:enqueue("
    message ++= handle.sender.path.name + " -> "
    message ++= receiver.path.name + ") Remainning: "

    println(message)
  }

  def afterEnqueue(queue: MessageQueue, receiver: ActorRef, handle: Envelope) {
  }

}

