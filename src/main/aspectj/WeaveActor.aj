package sample;

import akka.actor.ActorRef;
import akka.pattern.AskSupport;
import akka.actor.ActorSystem;
import akka.dispatch.Envelope;
import akka.dispatch.MessageQueue;

import scala.concurrent.impl.CallbackRunnable;

privileged public aspect WeaveActor {
  
    before():
    execution(* akka.actor.ActorSystem.actorOf(..))
  {
  }

    before(ActorRef receiver, Envelope handle):
    execution(* akka.dispatch.MessageQueue.enqueue(..)) &&
    args(receiver, handle, ..)
  {
      String message = handle.sender.path().name() + " -> " + receiver.path().name();
      System.out.println(message);
  }
  
    before(Runnable runnable):
    execution(* akka.dispatch.MessageDispatcher.execute(..)) &&
    args(runnable, ..)
  {
  }
  
}
