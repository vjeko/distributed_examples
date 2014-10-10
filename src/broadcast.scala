import akka.actor.{ Actor, ActorRef }
import akka.actor.ActorSystem
import akka.actor.Props

package object types {
  type NodeSetT = Set[ActorRef]
  type DeliveredT = Set[DataMessage]
}

// -- Initialization messages --
case class SetDestination(dst: ActorRef)
case class AddLink(link: ActorRef)

// -- Base message type --
case class DataMessage(seq: Int, data: String)

// -- main() -> Node messages --
case class Stop()
case class TriggerRBBroadcast(msg: DataMessage)

// -- Node -> Link messages --
case class TriggerPLSend(msg: DataMessage)

// -- Link -> Link messages --
case class PLSend(msg: DataMessage)

// -- Link -> Node messages ---
case class PLDeliver(msg: DataMessage)

/**
 * PerfectLink Actor.
 *
 * N.B. A Node (source) and its Links must be co-located.
 */
class PerfectLink(source: ActorRef) extends Actor {
  // N.B. destination is the destination Node's PerfectLink
  var destination : ActorRef = null

  def set_destination(dst: ActorRef) {
    destination = dst
  }

  def pl_send(msg: DataMessage) {
    // TODO(cs): need to implement PerfectPointToPointLinks rather than assuming
    // reliable delivery. Akka's `!` operator does *not* provide reliable delivery
    // according to:
    // http://doc.akka.io/docs/akka/snapshot/general/message-delivery-reliability.html
    destination ! PLSend(msg)
  }

  def receive = {
    case SetDestination(dst) => set_destination(dst)
    case TriggerPLSend(msg) => pl_send(msg)
    case _ => println("Unknown message")
  }

  // TODO(cs): send PL_Deliver to source
}

/**
 * Node Actor. Implements Reliable Broadcast.
 */
class Node(ID: Int) extends Actor {
  type LinkSetT = Set[ActorRef]
  type DeliveredT = Set[DataMessage]

  var allLinks: LinkSetT = Set()
  var delivered: DeliveredT = Set()

  def rb_broadcast(msg: DataMessage) {
    beb_broadcast(msg)
  }

  def beb_broadcast(msg: DataMessage) {
    allLinks.map(link => link ! TriggerPLSend(msg))
  }

  def rb_deliver(msg: DataMessage) {
    println("Reliable broadcast delivery of message " + msg + " to " + ID)
  }

  def beb_deliver(msg: DataMessage) {
    // TODO(cs): this is actually RBDeliver, not beb_deliver
    if (delivered contains msg) {
      return
    }

    delivered = delivered + msg
    rb_deliver(msg)
    beb_broadcast(msg)
  }

  def add_link(link: ActorRef) {
    allLinks = allLinks + link
  }

  def receive = {
    case AddLink(link) => add_link(link)
    case Stop => context.stop(self)
    case TriggerRBBroadcast(msg) => rb_broadcast(msg)
    case PLDeliver(msg) => beb_deliver(msg)
    case _ => println("Unknown message")
  }
}

object Main extends App {
  val system = ActorSystem("Broadcast")

  val ids = List.range(0, 5);
  val startFun = (i: Int) => system.actorOf(Props(new Node(i)))
  val nodes = ids.map(i => startFun(i))

  val createLinksForNodes = (src: ActorRef, dst: ActorRef) => {
    val l1 = system.actorOf(Props(new PerfectLink(src)))
    val l2 = system.actorOf(Props(new PerfectLink(dst)))
    l1 ! SetDestination(l2)
    l2 ! SetDestination(l1)
    src ! AddLink(l1)
    dst ! AddLink(l2)
  }
  // N.B. nodes have links to themselves.
  val pairs = for(x <- nodes; y <- nodes) yield (x, y)
  pairs.map(tuple => createLinksForNodes(tuple._1, tuple._2))

  nodes(0) ! TriggerRBBroadcast(DataMessage(1, "Message"))
  nodes(2) ! TriggerRBBroadcast(DataMessage(2, "Message"))
  nodes(1) ! Stop
  nodes(3) ! TriggerRBBroadcast(DataMessage(3, "Message"))
  nodes(2) ! TriggerRBBroadcast(DataMessage(4, "Message"))
  nodes(4) ! TriggerRBBroadcast(DataMessage(5, "Message"))
}
