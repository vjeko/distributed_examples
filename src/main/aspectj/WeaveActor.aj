package sample;

import static java.lang.Thread.sleep;

import akka.actor.ActorRef;
import akka.actor.Actor;
import akka.actor.ActorCell;
import akka.pattern.AskSupport;
import akka.actor.ActorSystem;
import akka.dispatch.Envelope;
import akka.dispatch.MessageQueue;

import scala.concurrent.impl.CallbackRunnable;

		
		///try{
		//    sleep(1500);
		//} catch (InterruptedException e){}

privileged public aspect WeaveActor {
  
    before(MessageQueue me, ActorRef receiver, Envelope handle):
    execution(* akka.dispatch.MessageQueue.enqueue(..)) &&
    args(receiver, handle, ..) && this(me)
  {
      String message =
      	"MessageQueue:enqueue(" + 
      	handle.sender.path().name()
      	+ " -> " + receiver.path().name() + ") Remainning: "
      	+ me.numberOfMessages();
      System.out.println(message);
  }
 
  
    before(ActorCell receiver, Envelope invocation):
    execution(* akka.dispatch.MessageDispatcher.dispatch(..)) &&
    args(receiver, invocation, ..)
  {
  
  		Actor actor = receiver.actor();
  		if (actor == null) return;
  		
    	System.out.println("before | dispatch(" 
    		+ actor.self().path().name() + ")");
  
  }
  
    after(ActorCell receiver, Envelope invocation):
    execution(* akka.dispatch.MessageDispatcher.dispatch(..)) &&
    args(receiver, invocation, ..)
  {
  
  		Actor actor = receiver.actor();
  		if (actor == null) return;
  		
    	System.out.println("after  | dispatch(" 
    		+ actor.self().path().name() + ")");
  
  }

}
