
import broadcast.Node,
       broadcast.NodeTest,
       broadcast.Init,
       broadcast.RB_Broadcast,
       broadcast.DataMessage

import pastry._

import akka.actor.Actor,
       akka.actor.ActorRef,
       akka.actor.DeadLetter,
       akka.actor.ActorSystem,
       akka.actor.Props
       
import akka.dispatch.verification.Instrumenter,
       akka.dispatch.verification.ExternalEvent,
       akka.dispatch.verification.Start,
       akka.dispatch.verification.Send,
       akka.dispatch.verification.DPORwFailures,
       akka.dispatch.verification.Unique,
       akka.dispatch.verification.WaitQuiescence,
       akka.dispatch.verification.PastryInvariantChecker

import scala.collection.immutable.Vector,
       scala.collection.mutable.Queue

import akka.dispatch.verification.NetworkPartition,
       akka.dispatch.verification.Scheduler,
       akka.dispatch.verification.Util

import java.io._

import akka.actor.Actor,
       akka.actor.ActorRef,
       akka.actor.DeadLetter,
       akka.actor.ActorSystem,
       akka.actor.Props

import org.slf4j.LoggerFactory,
       com.typesafe.scalalogging.Logger



class ResultAggregator {
  
  val traceFN = "trace.txt"
  val typeFN = "types.txt"
  val graphFN = "graph.txt"
  
  write(traceFN, "")
  write(typeFN, "")
  write(graphFN, "")
  
  val traces : Queue[Array[Unique]] = new Queue()
  val traceLog = new java.io.PrintWriter(new FileWriter(traceFN, true))
  val typeLog = new java.io.PrintWriter(new FileWriter(typeFN, true))
  val graphLog = new java.io.PrintWriter(new FileWriter(graphFN, true))

  
  def write(name: String, content: String) {
    val writer = new java.io.PrintWriter(name);
    writer.print(content);
    writer.close();
  }
  
  
  def dumpTraces() {
    
    for (trace <- traces) {
      var line = ""
      trace.foreach( e => line ++= e.id + " ")
      traceLog.println(line)
    }
    
    traces.clear()
  }
  
  
  def collect(trace: Queue[Unique]) : Unit = {
    traces.enqueue( trace.clone().toArray )
    
    if (traces.size < 500) return
    dumpTraces()
  }
  
  
  def done(scheduler: Scheduler) : Unit = {
    dumpTraces()
    
    val dpor = scheduler.asInstanceOf[DPORwFailures]
    for(node <- dpor.depGraph.nodes) {
      typeLog.println(node.value.id + " " + node.value)
    }
    
    graphLog.print( Util.getDot(dpor.depGraph) )
    
    typeLog.close()
    traceLog.close()
    graphLog.close()
  }
}


object PastryBug extends App with Config
{


  val collector = new ResultAggregator
  val scheduler = new DPORwFailures
  
  scheduler.invariantChecker = new PastryInvariantChecker
  
  Instrumenter().scheduler = scheduler
  Instrumenter().tellEnqueue = new akka.dispatch.verification.TellEnqueueBusyWait
  
  val logger = Logger(LoggerFactory.getLogger("pastry"))
  val system = ActorSystem("pastry")
  
  val bootstrapID : BigInt = 1
  val bootstrapSpawn = Start(Props[PastryPeer], name = toBase(bootstrapID))
  val bootstrapInit = Send(toBase(bootstrapID), () => Bootstrap(bootstrapID, bootstrapID))
  
  val bootstrapID2 : BigInt = 10
  val bootstrapSpawn2 = Start(Props[PastryPeer], name = toBase(bootstrapID2))
  val bootstrapInit2 = Send(toBase(bootstrapID2), () => Bootstrap(bootstrapID2, bootstrapID))
  
  val bootstrapID3 : BigInt = 3
  val bootstrapSpawn3 = Start(Props[PastryPeer], name = toBase(bootstrapID3))
  val bootstrapInit3 = Send(toBase(bootstrapID3), () => Bootstrap(bootstrapID3, bootstrapID))
  
  val bootstrapID4 : BigInt = 5
  val bootstrapSpawn4 = Start(Props[PastryPeer], name = toBase(bootstrapID4))
  val bootstrapInit4 = Send(toBase(bootstrapID4), () => Bootstrap(bootstrapID4, bootstrapID))

  val externalEvents : Vector[ExternalEvent] = (Vector() :+
    bootstrapSpawn :+ bootstrapInit :+ 
    WaitQuiescence :+ 
    bootstrapSpawn2 :+ bootstrapInit2 :+ 
    WaitQuiescence :+
    bootstrapSpawn3 :+ bootstrapInit3 :+ 
    WaitQuiescence :+
    bootstrapSpawn4 :+ bootstrapInit4
    )
    
  scheduler.run(
      externalEvents, 
      collector.collect, 
      collector.done)
}


object Simple// extends App
{

  
  val scheduler = new DPORwFailures
  Instrumenter().scheduler = scheduler
  Instrumenter().tellEnqueue = new akka.dispatch.verification.TellEnqueueBusyWait
  
  val names = List.range(0, 3).map(i => "A-" + i.toString())
  
  val spawns = names.map(i => Start(Props[NodeTest], name = i))
  val rb0 = Send(names(0), () => DataMessage(1, "Message"))
  val rb1 = Send(names(1), () => DataMessage(2, "Message"))
  val rb2 = Send(names(2), () => DataMessage(3, "Message"))
  val rb3 = Send(names(0), () => DataMessage(4, "Message"))
  
  val a = Set(names(0))
  val b = Set(names(1), names(2))
  val par = NetworkPartition(a, b)
  
  val externalEvents : Vector[ExternalEvent] = Vector() ++
    spawns :+ rb0 :+ rb1 :+ rb2 :+ rb3 :+ par
    
  val collector = new ResultAggregator
    scheduler.run(
      externalEvents, 
      collector.collect, 
      collector.done)

}
