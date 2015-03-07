package pastry

import spire.math._

import scala.collection.mutable.HashSet

import akka.actor.Actor,
       akka.actor.ActorRef,
       akka.actor.DeadLetter,
       akka.actor.ActorSystem,
       akka.actor.Props
       
trait Config {
  
  val base = 3
  val exponent = 3
  val modulo = Natural(base).pow(exponent)
  
  def internalErrorF = throw new Exception("internal error")
  
  
  object State extends Enumeration {
    type State = Value
    val Offline, Joining, PreOnline, Online = Value
  }
  

  def commonPrefix(s1: String, s2: String) : Int = {
    
    assert(s1 != s2)
    assert(s1.size == exponent)
    
      for (i <- 0 to exponent - 1) {
        if (s1.charAt(i) != s2.charAt(i))
        return i + 1
    }
    
    return 0
  }
  
  
  def closest(set: HashSet[BigInt], key: BigInt) : Option[BigInt] = {
    if (set.isEmpty) return None
    return Some(set.minBy(v => (v - key).abs))
  }
  
  
  def charToIndex(char: Char) : Int = {
    return Integer.parseInt(char.toString(), base)
  }
  
  
  def fromBase(number: String) : BigInt = {
    return Integer.parseInt(number, base)
  }
  
  def toBase(int : BigInt) : String = {
    val str = (int % modulo).toString(base)
    return "0" * (exponent - str.size) ++ str
  }
}