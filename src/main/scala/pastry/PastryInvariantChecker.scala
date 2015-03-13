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
  var onlineSet = new HashSet[String]
  
  
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
    
    val newOnlineSet = new HashSet[String]
    
    for((name, actor) <- actors) {
      assert(actor.isInstanceOf[PastryPeer])
      val peer = actor.asInstanceOf[PastryPeer]
      val config = actor.asInstanceOf[Config]
      
      if (peer.state == config.State.Online)
        newOnlineSet += peer.self.path.name
    }
        
    
    if (onlineSet == newOnlineSet) return
    
    onlineSet = newOnlineSet
    
    
    invariantOverlapImpl()
  }
  
  
  def invariantOverlapImpl() {
    val size = onlineSet.size
    if (size <= 1) return
    
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
              
      assert(aPeer.ls.rightNeighStr == bPeer.myIDStr)
      assert(aPeer.myIDStr == bPeer.ls.leftNeighStr)
      
    }
  }
}