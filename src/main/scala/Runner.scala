
import broadcast.FireStarter

import akka.actor.{ Actor, ActorRef, DeadLetter }
import akka.actor.ActorSystem
import akka.actor.Props
import akka.dispatch.verification._


object Main extends App {
  Instrumenter().scheduler = new akka.dispatch.verification.DPOR
  Instrumenter().tellEnqueue = new akka.dispatch.verification.TellEnqueueBusyWait

  val system = ActorSystem("Broadcast")
  val fireStarter = system.actorOf(
      Props(new FireStarter()),
      name="FS")
  fireStarter ! 0
}
