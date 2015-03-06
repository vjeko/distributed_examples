package pastry

import spire.math._,
       spire.random._,
       spire.syntax.literals._,
       spire.syntax.literals.radix._
       
import akka.actor.Actor,
       akka.actor.ActorRef,
       akka.actor.DeadLetter,
       akka.actor.ActorSystem,
       akka.actor.ActorSelection,
       akka.actor.Props

import scala.collection.mutable.HashMap,
       scala.collection.mutable.HashSet,
       scala.collection.mutable.ListBuffer

import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.Logger



class Peer extends Actor with Config {
  
  val logger = Logger(LoggerFactory.getLogger("pastry"))
  
  var myID : BigInt = 0
  var myIDStr = "NONE"
  
  var state = State.Offline
  
  val rt = new RoutingTable(myID)
  val ls = new LeafSet(myID)
  
  val store = new HashMap[BigInt, BigInt]
  val joinFuture = new HashSet[BigInt]  
  
  
  def getRef(peer: BigInt) : ActorSelection = {
    return getRef(toBase(peer))
  }
  
  def getRef(peerBase: String) : ActorSelection = {
    return context.actorSelection("../" + peerBase)
  }
  
  
  def handleJoinThis(msg : JoinRequest) : Unit = {
    val newPeer = msg.newPeer
    
    logger.trace(myIDStr + ": " + 
        "I am responsible for key " + toBase(newPeer))
    
    msg.visitedPeers += myID
    getRef(newPeer) ! JoinReply(msg.visitedPeers)
  }
  
  
  def handleJoinThat(msg : JoinRequest, nextPeer: BigInt) : Unit = {
    val newPeer = msg.newPeer
    msg.visitedPeers += myID
    
    logger.trace(myIDStr + ": " + toBase(nextPeer) + 
        " is responsible for key " + toBase(newPeer))
    getRef(nextPeer) ! msg
  }
  
  
  def handleJoinReply(sender : ActorRef, msg : JoinReply) = {
    logger.trace(myIDStr + ": " + "Visited nodes: " + msg.visitedPeers)
    
    for(peer <- msg.visitedPeers) {
      assert(peer != myID)
      
      joinFuture += peer
      getRef(peer) ! StateRequest(sender = myID, receiver = peer) 
    }
    
  }
  
  
  def getNext(key: BigInt) : Option[BigInt] = {
  
    val nextPeer : BigInt = 
      ls.closest(key).getOrElse(
      rt.computeNext(key).getOrElse(
      closest(rt.values ++ ls.values + myID, key).getOrElse(
      internalErrorF
    )))  
    
    nextPeer match {
      case nextPeer : BigInt if (nextPeer == myID || 
          (myID - key).abs < (nextPeer - key).abs) =>
        return None
      case nextPeer : BigInt =>
        return Some(nextPeer)
      case _ => internalErrorF
    }
  }
  
  
  def handleJoin(sender : ActorRef, msg : JoinRequest) = {
    val newPeer = msg.newPeer
    getNext(newPeer) match {
      case None => handleJoinThis(msg)
      case Some(nextPeer) => handleJoinThat(msg, nextPeer)
    }
  }
  
  
  def handleState(sender : ActorRef, msg : StateRequest) = {
    val reply = StateUpdate(myID, msg.sender, rt, ls)
    getRef(msg.sender) ! reply
  }
  
  
  def handleStateReply(sender : ActorRef, msg : StateUpdate) = {
    val sender = msg.sender
    
    assert(msg.sender != myID)
    
     /**
     *  Technically, we only need to steal the leaf set from the
     *  closest node. Adding any additional leaf sets does not do
     *  any harm.
     */
    rt.insert(sender)
    ls.insert(sender)
    
    rt.steal(msg.rt)
    ls.steal(msg.ls)
    
    if (state == State.Joining) {
      assert(joinFuture contains sender) 
      joinFuture -= sender
      if (joinFuture.isEmpty) {
        handleCompletion()
        logger.trace(myIDStr + ": " + "Going online...")
      }
    }
  }

  
  /**
   * Finally, the new node transmits a copy of its resulting 
   * state to each of the nodes found in its neighborhood set, 
   * leaf set, and routing table.
   */
  def handleCompletion(): Unit = {
    state = State.Online
    /**
     * Do we go online before or after the nodes have update the sates?
     */
    for(peer <- rt) 
      getRef(peer) ! StateUpdate(myID, fromBase(peer), this.rt, this.ls)
    for(peer <- ls) 
      getRef(peer) ! StateUpdate(myID, peer, this.rt, this.ls)
  }
    

  def handleBootstrap(sender : ActorRef, msg: Bootstrap) = {
    myID = msg.id
    myIDStr = toBase(myID)
    
    state = State.Joining
    msg.booststrapPeer match {
      case value if value == myID =>
        logger.trace(myIDStr + ": " + "Starting a new swarm." )
        state = State.Online
      case _ =>      
        logger.trace(myIDStr + ": " + "Sending " + msg + " to " + toBase(msg.booststrapPeer) )
        getRef(msg.booststrapPeer) ! JoinRequest(myID, ListBuffer())
      }
  }
  
  
  def handleWriteRequest(sender : ActorRef, msg : WriteRequest) = {
    getNext(msg.key) match {
      case None =>
        logger.trace(myIDStr + ": " + "Wrote " + msg.key + " <- " + msg.value)
        store(msg.key) = msg.value
      case Some(nextPeer) => getRef(nextPeer) ! msg
    }
  }
  
  
  def handleReadRequest(sender : ActorRef, msg : ReadRequest) = {
    getNext(msg.key) match {
      case None => 
        val value = store(msg.key)
        logger.trace(myIDStr + ": " + "Read " + msg.key + " == " + value)
      case Some(nextPeer) => getRef(nextPeer) ! msg
    }
  }
  
  
  def receive = {
    
    // External API:
    case msg: Bootstrap => handleBootstrap(sender, msg)
    
    assert(myID != 0)
    
    case Write(key, value) => handleWriteRequest(sender, WriteRequest(myID, key, value))
    case Read(key) => handleReadRequest(sender, ReadRequest(myID, key))
        
    // Internal API:
    case msg : WriteRequest=> handleWriteRequest(sender, msg)
    case msg : ReadRequest=> handleReadRequest(sender, msg)
    
    case msg : JoinRequest => handleJoin(sender, msg)
    case msg : JoinReply => handleJoinReply(sender, msg)
    
    case msg : StateRequest => handleState(sender, msg)
    case msg : StateUpdate => handleStateReply(sender, msg)
    
    case other => throw new Exception("unknown message " + other)
  }

  
}