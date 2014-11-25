package akka.dispatch.verification

import akka.actor.ActorCell,
       akka.actor.ActorSystem,
       akka.actor.ActorRef,
       akka.actor.Actor,
       akka.actor.PoisonPill,
       akka.actor.Props

import akka.dispatch.Envelope,
       akka.dispatch.MessageQueue,
       akka.dispatch.MessageDispatcher

import scala.collection.concurrent.TrieMap,
       scala.collection.mutable.Queue,
       scala.collection.mutable.HashMap,
       scala.collection.mutable.HashSet,
       scala.collection.mutable.ArrayBuffer,
       scala.collection.mutable.ArraySeq,
       scala.collection.Iterator

import scalax.collection.mutable.Graph,
       scalax.collection.GraphPredef._, 
       scalax.collection.GraphEdge._,
       scalax.collection.edge.LDiEdge,
       scalax.collection.edge.Implicits._
       
import java.io.{ PrintWriter, File }

import scalax.collection.edge.LDiEdge,
       scalax.collection.edge.Implicits._,
       scalax.collection.io.dot._
       
import com.typesafe.scalalogging.LazyLogging

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;


// A basic scheduler
class DPOR extends Scheduler with LazyLogging {
  
  def urlses(cl: ClassLoader): Array[java.net.URL] = cl match {
    case null => Array()
    case u: java.net.URLClassLoader => u.getURLs() ++ urlses(cl.getParent)
    case _ => urlses(cl.getParent)
  }

  var instrumenter = Instrumenter
  var currentTime = 0
  var index = 0
  
  var iterCount = 0
  
  var producedEvents = new Queue[ Event ]
  var consumedEvents = new Queue[ Event ]
  
  var trace = new Queue[ Event ]
  
  var nextEvents = new Queue[ Event ]
  var parentEvent : Event = null
  
  // Current set of enabled events.
  val pendingEvents = new HashMap[String, Queue[(Event, ActorCell, Envelope)]]  
  val actorNames = new HashSet[String]
 
  val g = Graph[Event, DiEdge]()
  
  var dep = new HashMap[Event, HashMap[Event, Event]]
  var explored = new HashSet[Event]
  var backTrack = new ArraySeq[ (Queue[Integer], List[Event]) ](100)
  var freeze = new ArraySeq[ Boolean ](100)    
  val alreadyExplored = new HashSet[(Event, Event)]
  val alreadyExplored2 = new HashSet[(Integer, Integer)]
  var invariant : Queue[Integer] = Queue()
  
  freeze.map { f => false }
  
  
  def isSystemCommunication(sender: ActorRef, receiver: ActorRef): Boolean = {

    if (receiver == null) return true
    
    return sender match {
      case null => 
        isSystemMessage("deadletters", receiver.path.name)
      case _ =>
        isSystemMessage(sender.path.name, receiver.path.name)
    }
    
  }
  
  // Is this message a system message
  def isSystemMessage(sender: String, receiver: String): Boolean = {
    if ((actorNames contains sender) || (actorNames contains receiver)) {
      return false
    }
    
    return true
  }
  
  
  // Notification that the system has been reset
  def start_trace() : Unit = {
  }
  
  
  // When executing a trace, find the next trace event.
  private[this] def mutable_trace_iterator(
      trace: Queue[  Event ]) : Option[Event] = { 
    
    if(trace.isEmpty) return None
    return Some(trace.dequeue)
  }
  
  

  // Get next message event from the trace.
  def get_next_trace_message() : Option[MsgEvent] = {
    mutable_trace_iterator(nextEvents) match {
      case Some(v : MsgEvent) => Some(v)
      case Some(v : Event) => get_next_trace_message()
      case None => None
    }
  }
  
  
  
  // Figure out what is the next message to schedule.
  def schedule_new_message() : Option[(ActorCell, Envelope)] = {
    
    // Filter for messages belong to a particular actor.
    def is_the_same(e: MsgEvent, c: (Event, ActorCell, Envelope)) : Boolean = {
      c match {
        case (event :MsgEvent, cell, env) =>
          if (e.id == 0) e.receiver == cell.self.path.name
          else e.receiver == cell.self.path.name && e.id == event.id
        case _ => throw new Exception("not a message event")
      }
      
    }

    // Get from the current set of pending events.
    def get_pending_event(): Option[(Event, ActorCell, Envelope)] = {
      // Do we have some pending events
      pendingEvents.headOption match {
        case Some((receiver, queue)) =>

          if (queue.isEmpty == true) {
            
            pendingEvents.remove(receiver) match {
              case Some(key) => get_pending_event()
              case None => throw new Exception("internal error")
            }

          } else {
            Some(queue.dequeue())
            
          }
        case None => None
      }
    }
    
    def queuetoStr(queue: Queue[(Event, ActorCell, Envelope)]) : String = {
      var str = " "
      for((item, _ , _) <- queue) {
        item match {
          case m : MsgEvent => str += m.id + " "
        }
      }
      return str
    }

    val result = get_next_trace_message() match {
      // The trace says there is something to run.
      case Some(msg_event: MsgEvent) =>
        
        pendingEvents.get(msg_event.receiver) match {
          case Some(queue) =>
            val result = queue.dequeueFirst(is_the_same(msg_event, _))
            if (result == None ) {
              logger.trace( "queue size " + queue.size )
              for ((s, item) <- pendingEvents) item match {
                case q => 
                  logger.trace( "queue content: " + queuetoStr(q) ) 
              }
                 
              
            }
            result
          case None =>  throw new Exception("replay mismatch")
        }
      // The trace says there is nothing to run so we have either exhausted our
      // trace or are running for the first time. Use any enabled transitions.
      case None => get_pending_event()
    }
    
    result match {
      case Some((next_event, c, e)) =>
        
        next_event match {
          case m : MsgEvent =>
            logger.trace( Console.GREEN + "Now playing: " + m.id + Console.RESET )
            
            trace += m
            
            if (!invariant.isEmpty) {
              if(invariant.head == m.id) {
                logger.info("Replaying a message " + invariant.head)
                invariant.dequeue()
              }
            }
        }
        
        (g get next_event)
        parentEvent = next_event
        return Some((c, e))
      case _ => return None
    }
    
    
  }
  
  
  // Get next event
  def next_event() : Event = {
    mutable_trace_iterator(nextEvents) match {
      case Some(v) => v
      case None => throw new Exception("no previously consumed events")
    }
  }
  

  // Record that an event was consumed
  def event_consumed(event: Event) = {
    consumedEvents.enqueue( event )
  }
  
  
  def event_consumed(cell: ActorCell, envelope: Envelope) = {
    var event = new MsgEvent(
        envelope.sender.path.name, cell.self.path.name, 
        envelope.message)
    consumedEvents.enqueue( event )
  }
  
  
  // Record that an event was produced 
  def event_produced(event: Event) = {
        
    event match {
      case event : SpawnEvent => actorNames += event.name
      case msg : MsgEvent => 
    }
    
    producedEvents.enqueue( event )
    currentTime += 1
  }
  
  
  def getMessage(cell: ActorCell, envelope: Envelope) : MsgEvent = {
    
    val snd = envelope.sender.path.name
    val rcv = cell.self.path.name
    
    val msg = new MsgEvent(snd, rcv, envelope.message, 0)
    val msgs = pendingEvents.getOrElse(rcv, new Queue[(Event, ActorCell, Envelope)])
    
    val parent = parentEvent match {
      case null => 
        val newMsg = MsgEvent("null", "null", null, 0)
        dep.getOrElseUpdate(newMsg, new HashMap[Event, Event])
        newMsg
      case _ =>
        parentEvent
    }
    
    
    val parentMap = dep.get(parent) match {
      case Some(x) => x
      case None => throw new Exception("no such parent")
    }

    val realMsg = parentMap.get(msg) match {
      case Some(x : MsgEvent) =>   x
      case None =>
        val newMsg = new MsgEvent(msg.sender, msg.receiver, msg.msg)
        
        logger.warn(
            Console.YELLOW + "Not seen: " + newMsg.id + 
            " (" + newMsg.sender + " -> " + newMsg.receiver + ") " + Console.RESET)
            
        dep(newMsg) = new HashMap[Event, Event]
        parentMap(msg) = newMsg
        newMsg
      case _ => throw new Exception("wrong type")
    }
    
    pendingEvents(rcv) = msgs += ((realMsg, cell, envelope))
    return realMsg
  }
  
  
  
  def event_produced(cell: ActorCell, envelope: Envelope) = {

    val event = getMessage(cell, envelope)
    
    logger.trace(Console.BLUE + "New event: " + event.id + Console.RESET)
    
    g.add(event)
    producedEvents.enqueue( event )
    
    if(parentEvent != null) {
      g.addEdge(event, parentEvent)(DiEdge)
    }
  }
  
  
  // Called before we start processing a newly received event
  def before_receive(cell: ActorCell) {
    //logger.trace(Console.GREEN 
    //    + " ↓↓↓↓↓↓↓↓↓ ⌚  " + currentTime + " | " + cell.self.path.name + " ↓↓↓↓↓↓↓↓↓ " +
    //    Console.RESET)
  }
  
  
  // Called after receive is done being processed 
  def after_receive(cell: ActorCell) {
    //logger.trace(Console.RED 
    //    + " ↑↑↑↑↑↑↑↑↑ ⌚  " + currentTime + " | " + cell.self.path.name + " ↑↑↑↑↑↑↑↑↑ " 
    //    + Console.RESET)
  }
  
  def get_dot() {
    val root = DotRootGraph(
        directed = true,
        id = Some("DPOR"))

    def nodeStr(event: Event) : String = {
      event.value match {
        case msg : MsgEvent => msg.receiver + " (" + msg.id.toString() + ")" 
        case spawn : SpawnEvent => spawn.name + " (" + spawn.id.toString() + ")" 
      }
    }
    
    def nodeTransformer(
        innerNode: scalax.collection.Graph[Event, DiEdge]#NodeT):
        Option[(DotGraph, DotNodeStmt)] = {
      val descr = innerNode.value match {
        case msg : MsgEvent => DotNodeStmt( nodeStr(msg), Seq.empty[DotAttr])
        case spawn : SpawnEvent => DotNodeStmt( nodeStr(spawn), Seq(DotAttr("color", "red")))
      }

      Some(root, descr)
    }
    
    def edgeTransformer(
        innerEdge: scalax.collection.Graph[Event, DiEdge]#EdgeT): 
        Option[(DotGraph, DotEdgeStmt)] = {
      
      val edge = innerEdge.edge

      val src = nodeStr( edge.from.value )
      val dst = nodeStr( edge.to.value )

      return Some(root, DotEdgeStmt(src, dst, Nil))
    }
    
    val str = g.toDot(root, edgeTransformer, cNodeTransformer = Some(nodeTransformer))
    
    val pw = new PrintWriter(new File("dot.dot" ))
    pw.write(str)
    pw.close
  }

  
  
  def printPath(path : List[g.NodeT]) : String = {
    var pathStr = ""
    for(node <- path) {
      node.value match {
        case x : MsgEvent => pathStr += x.id + " "
        case _ => throw new Exception("internal error not a message")
      }
    }
    return pathStr
  }
  
  def printTrace(events : Queue[Event]) : String = {
    var str = ""
    for (item <- events) {
      item match {
        case m : MsgEvent => str += m.id + " " 
        case _ =>
      }
    }
    
    return str
  }
  
  
  def notify_quiescence() {
    
    //get_dot()
    currentTime = 0
    
    var str1 = "trace: "
    for (item <- trace) {
      item match {
        case m : MsgEvent => str1 += m.id + " " 
        case _ =>
      }
    }
    
    logger.info("-------------------------------------------------")
    var nnnn = dpor()
    logger.info("-------------------------------------------------")

    iterCount += 1
    
    // XXX: JUST A QUICK FIX. MAGIC NUMBER AHEAD.
    nextEvents.clear()
    nextEvents ++= consumedEvents.take(8)
    
    for (e <- nextEvents) e match {
      case m :MsgEvent => m.id = 0
      case _ =>
     }

    nextEvents ++= nnnn.drop(1)
    
    logger.trace(Console.RED + "Current trace: " +
        printTrace(trace) + Console.RESET)
    
    logger.trace(Console.RED + "Next trace:  " + 
        printTrace(nextEvents) + Console.RESET)
    
    producedEvents.clear()
    consumedEvents.clear()
  
    trace.clear()
    parentEvent = null
    pendingEvents.clear()
    if (iterCount < 3000) {
      instrumenter().await_enqueue()
      instrumenter().restart_system()
    }

  }
  
  
  
  def getEvent(index: Integer) : MsgEvent = {
    trace(index) match {
      case eee : MsgEvent => eee
      case _ => throw new Exception("internal error not a message")
    }
  }

  
  
  def dpor() : Queue[Event] = {
    
    val root = getEvent(0)
    val rootN = ( g get root )
    
    val freezeSet = new ArrayBuffer[Integer]
    
    def analyize_dep(earlierI: Integer, laterI: Integer) : Unit = {
      
      val earlier = getEvent(earlierI)
      val later = getEvent(laterI)
      
      alreadyExplored += ((earlier, later))
      
      val earlierN = (g get earlier)
      val laterN = (g get later)
      
      val laterPath = laterN.pathTo( rootN ) match {
        case Some(path) => path.nodes.toList.reverse
        case None => throw new Exception("no such path")
      }
      
      val earlierPath = earlierN.pathTo( rootN ) match {
        case Some(path) => path.nodes.toList.reverse
        case None => throw new Exception("no such path")
      }
      
      val commonPrefix = laterPath.intersect(earlierPath)
      
      val laterDiff = laterPath.diff(commonPrefix)
      val earlierDiff = earlierPath.diff(commonPrefix)

      val needToReplay = 
        earlierDiff.take(earlierDiff.size - 1) ++ laterDiff
      
      val lastElement = commonPrefix.last
      val commonAncestor = trace.indexWhere { e => (e == lastElement.value) }
      
      require(commonAncestor > -1 && commonAncestor < laterI)
      
      val values = needToReplay.map(v => v.value)
      
      
      if(  !freeze(commonAncestor) &&
           !alreadyExplored.contains((later, earlier))
           ) {
      
        logger.trace(Console.CYAN + "Earlier: " + 
            printPath(earlierPath) + Console.RESET)
        logger.trace(Console.CYAN + "Later:   " + 
            printPath(laterPath) + Console.RESET)
        logger.trace(Console.CYAN + "Replay:  " + 
            printPath(needToReplay) + Console.RESET)

        logger.info("Found a race between " + earlier.id +  " and " + 
            later.id + " with a common index " + commonAncestor)
        
        freeze(commonAncestor) = true
        freezeSet += commonAncestor
        val pair: Queue[Integer] = Queue(later.id, earlier.id)
        backTrack(commonAncestor) = (pair, values)
      }

    }
    
    
      
    def isCoEnabeled(earlier: MsgEvent, later: MsgEvent) : Boolean = {
      
      val earlierN = (g get earlier)
      val laterN = (g get later)
      
      val coEnabeled = laterN.pathTo(earlierN) match {
        case None => true
        case _ => false
      }
      
      return coEnabeled
    }
    

    require(invariant.isEmpty)
    
    for(laterI <- 0 to trace.size - 1) {
      val later = getEvent(laterI)

      for(earlierI <- 0 to laterI - 1) {
        val earlier = getEvent(earlierI)
        
        if (earlier.receiver == later.receiver &&
            isCoEnabeled(earlier, later)) {
          analyize_dep(earlierI, laterI)
        }
        
      }
    }
    
    
    var maxIndex = 0
    for(i <- Range(0, backTrack.size -1)) {
      if (backTrack(i) != null) {
        maxIndex = i
      }
    }
    
    require(freeze(maxIndex) == true)
    freeze(maxIndex) = false
    invariant = backTrack(maxIndex)._1.clone()
    var tmp = backTrack(maxIndex)._1.clone()
    val tuple = (tmp.dequeue(), tmp.dequeue())
    
    logger.info("Next message ordering -> " + backTrack(maxIndex)._1)
    //require( !(alreadyExplored2 contains tuple) )
    alreadyExplored2 += tuple
    
    val result =  trace.take(maxIndex + 1) ++ backTrack(maxIndex)._2
    backTrack(maxIndex) = null
    return result
    
  }
  

}
