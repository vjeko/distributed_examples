
import broadcast.FireStarter

import akka.actor.{ Actor, ActorRef, DeadLetter }
import akka.actor.ActorSystem
import akka.actor.Props
import akka.dispatch.verification._


object Main extends App {
  Instrumenter().scheduler = new akka.dispatch.verification.DPOR
 
  val system = ActorSystem("Broadcast")
  val fireStarter = system.actorOf(
      Props(new FireStarter(system)),
      name="firestarter")
  fireStarter ! 0
  
}