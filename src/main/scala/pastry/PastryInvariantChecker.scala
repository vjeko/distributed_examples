package akka.dispatch.verification

import pastry._

import scala.collection.mutable.HashMap,
       scala.collection.mutable.HashSet,
       scala.collection.mutable.ArrayBuffer

import akka.actor.ActorCell,
       akka.actor.ActorSystem,
       akka.actor.ActorRef,
       akka.actor.Actor

import akka.dispatch.Envelope,
       akka.dispatch.MessageQueue,
       akka.dispatch.MessageDispatcher

case class PastryNeighInvariant() extends Invariant


class PastryInvariantChecker extends InvariantChecker {

  var actors = new HashMap[String, Any]  
  var onlineSet = new HashSet[String]
  
  
  def init(actorMap: HashMap[String, Any]) {
    actors = actorMap
  }
  
  
  def newRun() {
    onlineSet.clear()
  }
  
  def messageProduced(cell: ActorCell, envelope: Envelope) : Seq[Option[Invariant]] = {
    return ArrayBuffer(None)
  }
  
  def messageConsumed(cell: ActorCell, envelope: Envelope) : Seq[Option[Invariant]] = {
    val results = new ArrayBuffer[Option[Invariant]]
    results += invariantOverlap(cell, envelope)
    return results
  }
  
  
  /**
   * Key responsibility is divided equally according
   * to the distance between two neighboring nodes.
   * Therefore, no overlap between key-spaces of two 
   * adjacent nodes must exist.
   */
  def invariantOverlap(cell: ActorCell, envelope: Envelope) : Option[Invariant] = {
    
    val newOnlineSet = new HashSet[String]
    
    for((name, actor) <- actors) {
      assert(actor.isInstanceOf[PastryPeer])
      val peer = actor.asInstanceOf[PastryPeer]
      val config = actor.asInstanceOf[Config]
      
      if (peer.state == config.State.Online)
        newOnlineSet += peer.self.path.name
    }
        
    
    if (onlineSet == newOnlineSet) return None
    
    onlineSet = newOnlineSet
    return invariantOverlapImpl()
  }
  
  
  def invariantOverlapImpl() : Option[Invariant] = {
    val size = onlineSet.size
    if (size <= 1) return None
    
    val array = onlineSet.toList.sortBy { x => x }
    val it1 = Iterator.continually(array).flatten
    val it2 = Iterator.continually(array).flatten.drop(1)
    
    println("Number of nodes online -> " + size)
    for (i <- Range(0, size)) {
      val a = it1.next()
      val b = it2.next()
      val aPeer = actors(a).asInstanceOf[PastryPeer]
      val aConfig = actors(a).asInstanceOf[Config]
      val bPeer = actors(b).asInstanceOf[PastryPeer]
      val bConfig = actors(b).asInstanceOf[Config]

      println(aPeer.ls.leftNeighStr + ":" + aPeer.myIDStr + ":" + aPeer.ls.rightNeighStr + " <-> " + 
              bPeer.ls.leftNeighStr + ":" + bPeer.myIDStr + ":" + bPeer.ls.rightNeighStr)
              
      if(aPeer.ls.rightNeighStr != bPeer.myIDStr) return Some(PastryNeighInvariant())
      if(aPeer.myIDStr != bPeer.ls.leftNeighStr) return Some(PastryNeighInvariant())
    }
    
  return None
  }
  
}