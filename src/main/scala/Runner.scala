
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
       akka.dispatch.verification.Unique

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
  
  //val r0 = new RoutingTable(132)
  //r0.insertInt(1010)
  //val r1 = new RoutingTable(132)
  //r0.insertInt(1010)
  //println(r0 == r1)
  
  System.exit(0)
  val collector = new ResultAggregator
  val scheduler = new DPORwFailures
  
  Instrumenter().scheduler = scheduler
  Instrumenter().tellEnqueue = new akka.dispatch.verification.TellEnqueueBusyWait
  
  val logger = Logger(LoggerFactory.getLogger("pastry"))
  val system = ActorSystem("pastry")
  
  val bootstrapID : BigInt = 54615
  val bootstrapSpawn = Start(Props[PastryPeer], name = toBase(bootstrapID))
  val bootstrapInit = Send(toBase(bootstrapID), () => Bootstrap(bootstrapID, bootstrapID))
  
  val otherIDs : List[BigInt] = List(1, 3, 1234599, 5423)
  val otherSpawns = otherIDs.map(i => Start(Props[PastryPeer], toBase(i)))
  val otherInits = otherIDs.map(id => Send(toBase(id), () => Bootstrap(id, bootstrapID)))

  val externalEvents : Vector[ExternalEvent] = (Vector() :+
    bootstrapSpawn :+ bootstrapInit) ++ otherSpawns ++ otherInits
    
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
