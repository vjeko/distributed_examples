import akka.actor.{ Actor, ActorRef }
import akka.actor.{ ActorSystem, Scheduler, Props }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

// -- Initialization messages --
case class AddLink(link: ActorRef)
case class SetDestination(dst: ActorRef)
case class SetParentID(id: Int)

// -- Base message type --
object DataMessage {
  // Global static variable to simplify creation of unique IDs.
  private var next_id = 0
  private def get_next_id = {next_id += 1; next_id}
}

case class DataMessage(data: String) {
  var id = DataMessage.get_next_id
}

// -- main() -> Node messages --
case class Stop()
case class RBBroadcast(msg: DataMessage)

// -- Node -> Link messages --
case class PLSend(msg: DataMessage)

// -- Link -> Link messages --
case class SLDeliver(src: Int, msg: DataMessage)
case class ACK(msg_id: Int)
case class Tick()

// -- Link -> Node messages ---
case class PLDeliver(src: Int, msg: DataMessage)

// -- FailureDetector -> Link messages --
case class SuspectedFailure(node: ActorRef)
// N.B. even in a crash-stop failure model, SuspectedRecovery might still
// occur in the case that the FD realized that it made a mistake.
case class SuspectedRecovery(node: ActorRef)

/**
 * FailureDetector interface.
 *
 * Guarentee: eventually all suspects are correctly suspected. We don't know
 * when that point will be though.
 *
 * We use an unorthodox "push" interface for notifying clients of suspected
 * failures, rather than the traditional "pull" interface. This is to achieve
 * quiescence.
 */
trait FailureDetector {}

/**
 * FailureDetector implementation meant to be integrated directly into a model checker or
 * testing framework.
 */
class HackyFailureDetector(allLinks: Set[ActorRef]) extends Actor with FailureDetector {
  def receive = {
    case _ => println("Unknown message")
  }

  // TODO(cs): upon failing or recovering a node, send SuspectedFailure and
  // SuspectedRecovery messages to all links.
}

// Class variable for PerfectLink.
object PerfectLink {
  private val timeout_ms = 500
}

/**
 * PerfectLink Actor.
 *
 * N.B. A Node (parent) and its PerfectLinks must be co-located.
 */
class PerfectLink(parent: ActorRef, scheduler: Scheduler) extends Actor {
  // N.B. destination is the destination Node's PerfectLink
  var destination : ActorRef = null
  var parentID : Int = -1
  var delivered : Set[Int] = Set()
  var unacked : Map[Int,DataMessage] = Map()
  // Whether the destination is suspected to be crashed, according to a
  // FailureDetector.
  var destination_suspected = false

  def set_destination(dst: ActorRef) {
    destination = dst
  }

  def set_parent_id(id: Int) {
    parentID = id
  }

  def pl_send(msg: DataMessage) {
    sl_send(msg)
  }

  def sl_send(msg: DataMessage) {
    if (destination == null) {
      throw new RuntimeException("destination not yet configured")
    }
    if (parentID == -1) {
      throw new RuntimeException("parentID not yet configured")
    }
    destination ! SLDeliver(parentID, msg)
    if (unacked.size == 0) {
      scheduler.scheduleOnce(PerfectLink.timeout_ms milliseconds,
                             self,
                             Tick)
    }
    unacked += (msg.id -> msg)
  }

  def handle_sl_deliver(senderID: Int, msg: DataMessage) {
    destination ! ACK(msg.id)

    if (delivered contains msg.id) {
      return
    }

    delivered = delivered + msg.id
    parent ! PLDeliver(senderID, msg)
  }

  def handle_ack(msg_id: Int) {
    unacked -= msg_id
  }

  def handle_suspected_failure(suspect: ActorRef) {
    // TODO(cs): does == work for ActorRefs?
    if (suspect == destination) {
      destination_suspected = true
    }
  }

  def handle_suspected_recovery(suspect: ActorRef) {
    if (suspect == destination) {
      destination_suspected = false
    }
  }

  def handle_tick() {
    if (parentID == -1) {
      System.err.println("handle_tick(): parentID not yet set")
      return
    }
    if (destination_suspected) {
      return
    }
    unacked.values.map(msg => sl_send(msg))
    if (unacked.size != 0) {
      scheduler.scheduleOnce(PerfectLink.timeout_ms milliseconds,
                             self,
                             Tick)
    }
  }

  def stop() {
    context.stop(self)
  }

  def receive = {
    case SetDestination(dst) => set_destination(dst)
    case SetParentID(id) => set_parent_id(id)
    case PLSend(msg) => pl_send(msg)
    case SLDeliver(src, msg) => handle_sl_deliver(src, msg)
    case ACK(msg_id) => handle_ack(msg_id)
    case SuspectedFailure(destination) => handle_suspected_failure(destination)
    case SuspectedRecovery(destination) => handle_suspected_recovery(destination)
    case Stop => stop
    case Tick => handle_tick
    case _ => println("Unknown message")
  }
}

/**
 * Node Actor. Implements Reliable Broadcast.
 */
object Node {
  // Global static variable to simplify creation of unique IDs.
  private var next_id = 0
  private def get_next_id = {next_id += 1; next_id}
}

class Node extends Actor {
  var id = Node.get_next_id
  var allLinks: Set[ActorRef] = Set()
  var delivered: Set[Int] = Set()

  def add_link(link: ActorRef) {
    allLinks = allLinks + link
    link ! SetParentID(id)
  }

  def rb_broadcast(msg: DataMessage) {
    beb_broadcast(msg)
  }

  def beb_broadcast(msg: DataMessage) {
    allLinks.map(link => link ! PLSend(msg))
  }

  def handle_pl_deliver(src: Int, msg: DataMessage) {
    handle_beb_deliver(src, msg)
  }

  def handle_beb_deliver(src: Int, msg: DataMessage) {
    if (delivered contains msg.id) {
      return
    }

    delivered = delivered + msg.id
    println("RBDeliver of message " + msg + " from " + src + " to " + id)
    beb_broadcast(msg)
  }

  def stop() {
    allLinks.map(link => link ! Stop)
    context.stop(self)
  }

  def receive = {
    case AddLink(link) => add_link(link)
    case Stop => stop
    case RBBroadcast(msg) => rb_broadcast(msg)
    case PLDeliver(src, msg) => handle_pl_deliver(src, msg)
    case _ => println("Unknown message")
  }
}

object Main extends App {
  val system = ActorSystem("Broadcast")

  val nodes = List.range(0, 5).map(_ => system.actorOf(Props(new Node())))
  var links : Set[ActorRef] = Set()

  val createLinksForNodes = (src: ActorRef, dst: ActorRef) => {
    val l1 = system.actorOf(
      Props(new PerfectLink(src, system.scheduler)))
    val l2 = system.actorOf(
      Props(new PerfectLink(dst, system.scheduler)))
    // Can't pass the destination in the ctor because of a circular dependency.
    // Annoying that we have to asynchronously configure these objects...
    // we're not guarenteed that they'll be in a valid state!
    // There's probably a better way to do this.
    l1 ! SetDestination(l2)
    l2 ! SetDestination(l1)
    src ! AddLink(l1)
    dst ! AddLink(l2)
    links = links + l1
    links = links + l2
  }
  // N.B. nodes have links to themselves.
  val srcDstPairs = for(src <- nodes; dst <- nodes) yield (src, dst)
  srcDstPairs.map(tuple => createLinksForNodes(tuple._1, tuple._2))

  val fd = system.actorOf(Props(new HackyFailureDetector(links)))

  // TODO(cs): technically we should block here until all configuration
  // messages have been delivered. i.e. check that all Nodes have all their
  // Links.

  // Sample Execution:
  nodes(0) ! RBBroadcast(DataMessage("Message"))
  nodes(2) ! RBBroadcast(DataMessage("Message"))
  nodes(1) ! Stop
  nodes(3) ! RBBroadcast(DataMessage("Message"))
  nodes(2) ! RBBroadcast(DataMessage("Message"))
  nodes(4) ! RBBroadcast(DataMessage("Message"))
}
