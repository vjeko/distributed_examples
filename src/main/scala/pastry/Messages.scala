package pastry

import akka.actor.Actor,
       akka.actor.ActorRef,
       akka.actor.DeadLetter,
       akka.actor.ActorSystem,
       akka.actor.Props
       
import scala.collection.mutable.ListBuffer

abstract class Msg  

case class Bootstrap(id: BigInt, booststrapPeer: BigInt) extends Msg

case class Write(key : BigInt, value: BigInt) extends Msg
case class Read(key : BigInt) extends Msg

case class WriteRequest(sender: BigInt, key : BigInt, value: BigInt) extends Msg
case class WriteReply(key : BigInt, value: BigInt) extends Msg

case class ReadRequest(sender: BigInt, key: BigInt) extends Msg
case class ReadReply(key : BigInt, value: BigInt) extends Msg

case class JoinRequest(newPeer: BigInt, visitedPeers: ListBuffer[BigInt]) extends Msg
case class JoinReply(visitedPeers: ListBuffer[BigInt]) extends Msg

case class StateRequest(sender: BigInt, receiver: BigInt) extends Msg
case class StateUpdate(sender: BigInt, receiver: BigInt, rt: RoutingTable, ls: LeafSet) extends Msg