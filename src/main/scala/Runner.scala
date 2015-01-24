
import broadcast.Node,
       broadcast.NodeTest,
       broadcast.Init,
       broadcast.RB_Broadcast,
       broadcast.DataMessage

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



object Main// extends App
{

  val scheduler = new DPORwFailures
  
  Instrumenter().scheduler = scheduler
  Instrumenter().tellEnqueue = new akka.dispatch.verification.TellEnqueueBusyWait
  
  val names = List.range(0, 3).map(i => "A-" + i.toString())
  
  val spawns = names.map(i => Start(Props[Node], name = i))
  val inits = names.map(name => Send(name, Init(names.toSet)))
  val rb0 = Send(names(0), RB_Broadcast(DataMessage(1, "Message0")))
  val rb1 = Send(names(0), RB_Broadcast(DataMessage(1, "Message1")))
  
  val a = Set(names(0))
  val b = Set(names(1), names(2))
  val par = NetworkPartition(a, b)
  
  val externalEvents : Vector[ExternalEvent] = Vector() ++
    spawns ++ inits :+ rb0  :+ rb1 :+ par//
 
  val collector = new ResultAggregator
  scheduler.run(
      externalEvents, 
      collector.collect, 
      collector.done)
}


object Simple extends App
{

  val scheduler = new DPORwFailures
  Instrumenter().scheduler = scheduler
  Instrumenter().tellEnqueue = new akka.dispatch.verification.TellEnqueueBusyWait
  
  val names = List.range(0, 3).map(i => "A-" + i.toString())
  
  val spawns = names.map(i => Start(Props[NodeTest], name = i))
  val rb0 = Send(names(0), DataMessage(1, "Message"))
  val rb1 = Send(names(1), DataMessage(2, "Message"))
  val rb2 = Send(names(2), DataMessage(3, "Message"))
  val rb3 = Send(names(0), DataMessage(4, "Message"))
  
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
