import broadcast.Node
import broadcast.{Init, DataMessage, RB_Broadcast}

import akka.actor.{ Actor, ActorRef, DeadLetter }
import akka.actor.ActorSystem
import akka.actor.Props



object Main extends App {
  
  val system = ActorSystem("Broadcast")

  val ids = List.range(0, 5);
  val startFun = (i: Int) => 
    system.actorOf(Props[Node], name = "instrumented-" + i.toString())
  
  val nodes = ids.map(i => startFun(i))

  nodes.map(node => node ! Init(nodes.toSet))
  nodes.map(node => system.eventStream.subscribe(node, classOf[DeadLetter]) )

  nodes(0) ! RB_Broadcast(DataMessage(1, "Message"))

}