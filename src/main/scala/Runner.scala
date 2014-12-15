
import broadcast.Node,
       broadcast.Init,
       broadcast.RB_Broadcast,
       broadcast.DataMessage,
       broadcast.FireStarter

import akka.actor.Actor,
       akka.actor.ActorRef,
       akka.actor.DeadLetter,
       akka.actor.ActorSystem,
       akka.actor.Props
       
import akka.dispatch.verification.Instrumenter,
       akka.dispatch.verification.ExternalEvent,
       akka.dispatch.verification.Start,
       akka.dispatch.verification.Send,
       akka.dispatch.verification.DPOR

import scala.collection.immutable.Vector



object Main extends App {

  val scheduler = new DPOR
  Instrumenter().scheduler = scheduler
  Instrumenter().tellEnqueue = new akka.dispatch.verification.TellEnqueueBusyWait
  
  val names = List.range(0, 3).map(i => "A-" + i.toString())
  
  val spawns = names.map(i => Start(Props[Node], name = i))
  val inits = names.map(name => Send(name, Init(names.toSet)))
  val rb = Send(names(0), RB_Broadcast(DataMessage(1, "Message")))
      
  val externalEvents : Vector[ExternalEvent] = Vector() ++
    spawns ++ inits :+ rb
  
  scheduler.run(externalEvents)
}
