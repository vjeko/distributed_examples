
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
  //Instrumenter().tellEnqueue = new akka.dispatch.verification.TellEnqueueBusyWait
  
  val logger = Logger(LoggerFactory.getLogger("pastry"))
  val system = ActorSystem("pastry")
  
  val bootstrapID : BigInt = 1
  val bootstrapSpawn = Start(Props[PastryPeer], name = toBase(bootstrapID))
  val bootstrapInit = Send(toBase(bootstrapID), () => Bootstrap(bootstrapID, bootstrapID))
  
  val peerID1 : BigInt = 10
  val peerSpawn1 = Start(Props[PastryPeer], name = toBase(peerID1))
  val peerInit1 = Send(toBase(peerID1), () => Bootstrap(peerID1, bootstrapID))
  
  val peerID2 : BigInt = 3
  val peerSpawn2 = Start(Props[PastryPeer], name = toBase(peerID2))
  val peerInit2 = Send(toBase(peerID2), () => Bootstrap(peerID2, bootstrapID))
  
  val peerID3 : BigInt = 5
  val peerSpawn3 = Start(Props[PastryPeer], name = toBase(peerID3))
  val peerInit3 = Send(toBase(peerID3), () => Bootstrap(peerID3, bootstrapID))

  val peerID4 : BigInt = 20
  val peerSpawn4 = Start(Props[PastryPeer], name = toBase(peerID4))
  val peerInit4 = Send(toBase(peerID4), () => Bootstrap(peerID4, bootstrapID))
  
  val externalEvents : Vector[ExternalEvent] = (Vector() :+
    bootstrapSpawn :+ bootstrapInit :+ 
    new WaitQuiescence() :+ 
    peerSpawn1 :+ peerInit1 :+ 
    new WaitQuiescence() :+
    peerSpawn2 :+ peerInit2 :+ 
    //WaitQuiescence :+
    peerSpawn3 :+ peerInit3 :+
    //WaitQuiescence :+
    peerSpawn4 :+ peerInit4
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
