// TODO(cs): What's this deadletter stuff? Shouldn't we make that explicit rather than
// using Akka's protocol?
import akka.actor.{ Actor, ActorRef, DeadLetter }
import akka.actor.ActorSystem
import akka.actor.Props

package object types {
  type NodeSetT = Set[ActorRef]
  type DeliveredT = Set[DataMessage]
}

// Equivalent to "Die"
case class Stop()
case class Init(msg: Set[ActorRef])
// Generic message type.
case class DataMessage(seq: Int, data: String)
// "Reliable broadcast" message type defined in Algorithm 3.2
case class RB_Broadcast(msg: DataMessage)
// "Best-effort broadcast" messages type defined in Algorithm 3.1
case class BEB_Broadcast(msg: DataMessage)
case class BEB_Deliver(msg: DataMessage)

/**
 *
 * Local sending will just pass a reference to the
 * message inside the same JVM, without any restrictions
 * on the underlying object which is sent, whereas a
 * remote transport will place a limit on the message size.
 *
 * Messages which cannot be delivered (and for which this
 * can be ascertained) will be delivered to a synthetic actor
 * called /deadLetters. This delivery happens on a best-effort
 * basis; it may fail even within the local JVM (e.g. during
 * actor termination). Messages sent via unreliable network
 * transports will be lost without turning up as dead letters.
 *
 * An actor can subscribe to class akka.actor.DeadLetter on
 * the event stream, see Event Stream (Java) or Event Stream
 * (Scala) for how to do that. The subscribed actor will then
 * receive all dead letters published in the (local) system
 * from that point onwards. Dead letters are not propagated
 * over the network, if you want to collect them in one place
 * you will have to subscribe one actor per network node and
 * forward them manually. Also consider that dead letters are
 * generated at that node which can determine that a send
 * operation is failed, which for a remote send can be the
 * local system (if no network connection can be established)
 * or the remote one (if the actor you are sending to does
 * not exist at that point in time).
 *
 */
class Node(ID: Int) extends Actor {
  type NodeSetT = Set[ActorRef]
  type DeliveredT = Set[DataMessage]

  var allActors: NodeSetT = Set()
  var delivered: DeliveredT = Set()



  def rb_bradcast(msg: DataMessage) {
    beb_broadcast(msg)
  }


  def beb_broadcast(msg: DataMessage) {
    // TODO(cs): need to make explicit use of PerfectPointToPointLinks (see
    // Algorithm 3.1's dependency on Algorithm 2.2) rather than assuming
    // reliable delivery. Akka's `!` operator does *not* provide reliable delivery
    // according to:
    // http://doc.akka.io/docs/akka/snapshot/general/message-delivery-reliability.html
    // For an Akka implementation of PerfectPointToPointLinks, see:
    // http://doc.akka.io/docs/akka/snapshot/scala/persistence.html#at-least-once-delivery
    allActors.map(node => node ! BEB_Deliver(msg))
  }


  def rb_deliver(msg: DataMessage) {
    println("Reliable broadcast delivery of message " + msg + " to " + ID)
  }


  def beb_deliver(msg: DataMessage) {

    if (delivered contains msg) {
      return
    }

    delivered = delivered + msg
    rb_deliver(msg)
    beb_broadcast(msg)
  }


  def init(nodes: NodeSetT) {
    println("Initializing an actor with ID: " + ID);
    allActors = nodes
  }



  def receive = {
    case d: DeadLetter => allActors = allActors - d.recipient
    case Init(nodes) => init(nodes)
    case Stop => context.stop(self)
    case RB_Broadcast(msg) => rb_bradcast(msg)
    case BEB_Deliver(msg) => beb_deliver(msg)
    case _ => println("Unknown message")
  }
}

object Main extends App {

  val system = ActorSystem("Broadcast")

  val ids = List.range(0, 5);
  val startFun = (i: Int) => system.actorOf(Props(new Node(i)))
  val nodes = ids.map(i => startFun(i))

  nodes.map(node => node ! Init(nodes.toSet))
  nodes.map(node => system.eventStream.subscribe(node, classOf[DeadLetter]) )

  nodes(0) ! RB_Broadcast(DataMessage(1, "Message"))
  nodes(2) ! RB_Broadcast(DataMessage(2, "Message"))
  nodes(1) ! Stop
  nodes(3) ! RB_Broadcast(DataMessage(3, "Message"))
  nodes(2) ! RB_Broadcast(DataMessage(4, "Message"))
  nodes(4) ! RB_Broadcast(DataMessage(5, "Message"))

}
