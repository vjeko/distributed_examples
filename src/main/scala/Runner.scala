import broadcast.Node
import broadcast.FireStarter
import broadcast.{Init, DataMessage, RB_Broadcast}

import akka.actor.{ Actor, ActorRef, DeadLetter }
import akka.actor.ActorSystem
import akka.actor.Props



object Main extends App {
  
  val system = ActorSystem("Broadcast")
  val fireStarter = system.actorOf(
      Props(new FireStarter(system)),
      name="firestarter")
  fireStarter ! 0
  
}