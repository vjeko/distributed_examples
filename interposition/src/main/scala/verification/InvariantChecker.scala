package akka.dispatch.verification

import scala.collection.mutable.HashMap,
       scala.collection.mutable.HashSet

import akka.actor.ActorCell,
       akka.actor.ActorSystem,
       akka.actor.ActorRef,
       akka.actor.Actor

import akka.dispatch.Envelope,
       akka.dispatch.MessageQueue,
       akka.dispatch.MessageDispatcher

trait InvariantChecker {
  def init(actorMap: HashMap[String, Any])
  def newRun()
  def messageProduced(cell: ActorCell, envelope: Envelope)
  def messageConsumed(cell: ActorCell, envelope: Envelope)
}


class NullInvariantChecker extends InvariantChecker {
  def init(actorMap: HashMap[String, Any]) {}
  def newRun() {}
  def messageProduced(cell: ActorCell, envelope: Envelope) {}
  def messageConsumed(cell: ActorCell, envelope: Envelope) {}
}