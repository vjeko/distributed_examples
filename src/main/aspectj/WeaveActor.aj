package sample;

import akka.actor.ActorRef;
import akka.pattern.AskSupport;
import akka.actor.ActorSystem;

privileged public aspect WeaveActor {

  before(AskSupport askSupport, ActorRef actorRef, Object message):
    execution(* akka.pattern.AskSupport$class.ask(..)) &&
    args(askSupport, actorRef, message, ..)
  {
    String msg = message.toString();
    if (!msg.startsWith("InitializeLogger")) {
      System.out.println("Actor asked " + message);
    }
  }
  
    before():
    execution(* akka.actor.ActorSystem.actorOf(..))
  {
      //System.out.println("actorOf");
  }

    before():
    execution(* akka.dispatch.MessageQueue.enqueue(..))
  {
      //System.out.println("enqueue");
  }
  
}
