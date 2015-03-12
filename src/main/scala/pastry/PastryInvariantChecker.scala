package akka.dispatch.verification

import pastry._

import scala.collection.mutable.HashMap,
       scala.collection.mutable.HashSet

import akka.actor.ActorCell,
       akka.actor.ActorSystem,
       akka.actor.ActorRef,
       akka.actor.Actor

import akka.dispatch.Envelope,
       akka.dispatch.MessageQueue,
       akka.dispatch.MessageDispatcher



class PastryInvariantChecker extends InvariantChecker {

  var actors = new HashMap[String, Any]  
  val onlineSet = new HashSet[String]
  
  
  def init(actorMap: HashMap[String, Any]) {
    actors = actorMap
  }
  
  
  def newRun() {
    onlineSet.clear()
  }
  
  
  def messageProduced(cell: ActorCell, envelope: Envelope) {
  }
  
  
  def messageConsumed(cell: ActorCell, envelope: Envelope) {
    invariantOverlap(cell, envelope)
  }
  
  
  /**
   * Key responsibility is divided equally according
   * to the distance between two neighboring nodes.
   * Therefore, no overlap between key-spaces of two 
   * adjacent nodes must exist.
   */
  def invariantOverlap(cell: ActorCell, envelope: Envelope) {
    
    for((name, actor) <- actors) {
      assert(actor.isInstanceOf[PastryPeer])
      val peer = actor.asInstanceOf[PastryPeer]
      val config = actor.asInstanceOf[Config]
      
      if (peer.state == config.State.Online)
        onlineSet += peer.self.path.name
        
    }
        
  }
  
}