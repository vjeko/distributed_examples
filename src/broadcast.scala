import akka.actor.{ Actor, ActorRef }
import akka.actor.ActorSystem
import akka.actor.Props

package object types {
  type NodeListT = List[ActorRef]
  type DeliveredT = Set[DataMessage]
}

case class Init(msg: List[ActorRef])
case class DataMessage(seq: Int, data: String)
case class RB_Broadcast(msg: DataMessage)
case class BEB_Broadcast(msg: DataMessage)
case class BEB_Deliver(msg: DataMessage)



class Node(ID: Int) extends Actor {
  type NodeListT = List[ActorRef]
  type DeliveredT = Set[DataMessage]
  
  var allActors: NodeListT = List()
  var delivered: DeliveredT = Set()
  
  
  def rb_bradcast(msg: DataMessage) {
    beb_broadcast(msg)
  }
  
  
  def beb_broadcast(msg: DataMessage) {
    allActors.map(node => node ! BEB_Deliver(msg))
  }
  

  def rb_deliver(msg: DataMessage) {
   println("Reliable broadcast delivery of mesage " + msg)
  }
  
  
  def beb_deliver(msg: DataMessage) {

    if(delivered contains msg) {
      return;
    }
    
    delivered = delivered + msg
    rb_deliver(msg)
    beb_broadcast(msg)
  }
  

  def init(nodes: NodeListT) {    
    println("Initializing an actor with ID: " + ID);
    allActors = nodes
  }
  

  def receive = {
    case Init(nodes)       => init(nodes)
    case RB_Broadcast(msg) => rb_bradcast(msg)
    case BEB_Deliver(msg)  => beb_deliver(msg)
    case _                 => println("Unknown message")
  }
}

object Main extends App {
  val system = ActorSystem("Broadcast")

  val ids = List.range(1, 6);
  val startFun = (i: Int) => system.actorOf(Props(new Node(i)))
  val nodes = ids.map(i => startFun(i))
  
  nodes.map(node => node ! Init(nodes))
  nodes(0) ! RB_Broadcast(DataMessage(1, "Message One"))
  nodes(1) ! RB_Broadcast(DataMessage(2, "Message Two"))

}