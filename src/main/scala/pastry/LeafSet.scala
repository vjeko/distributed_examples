package pastry

import scala.collection.mutable.PriorityQueue

class LeafSet(ID : BigInt) extends Config with Traversable[BigInt] {
  
  val myID : BigInt = ID
  val myIDStr = toBase(ID)
  
  val L : Int = 4
  var smaller = scala.collection.mutable.PriorityQueue[BigInt]()(Ordering.by(x => -x))
  var bigger = scala.collection.mutable.PriorityQueue[BigInt]()(Ordering.by(x => x))
  
  def values : PriorityQueue[BigInt] = smaller ++ bigger
  
  
  override def equals (other: Any) = other match {
    case otherStruct : LeafSet => 
      myID == otherStruct.myID &&
      smaller.toArray.deep == otherStruct.smaller.toArray.deep &&
      bigger.toArray.deep == otherStruct.bigger.toArray.deep
    case _ => false
  }
  
  
  def foreach[U](f: BigInt => U) = 
    for(value <- smaller ++ bigger)
      f(value)
  
  
  override def clone() : LeafSet = {
    val newLS = new LeafSet(myID)
    newLS.smaller = smaller.clone()
    newLS.bigger = bigger.clone()
    return newLS
  }
  
  
  def closest(rawKey: BigInt) : Option[BigInt] = {
    val key = rawKey % modulo
    assert(key != myID)
    
    key < myID match {
      
      case true =>
        
        if (smaller.isEmpty || key < smaller.head)
          return None
          
        return Some(smaller.minBy(v => (v - key).abs))
        
      case false =>

        if (bigger.isEmpty || key > bigger.head)
          return None
          
        return Some(bigger.minBy(v => (v - key).abs))
    }

  }
  
  
  def steal(other: LeafSet) : Unit =
    for(value <- other.bigger ++ other.smaller) value match {
      case same : BigInt if same == myID =>
      case other : BigInt => this.insert(other)
      case _ => internalErrorF
    }
  
  
  def insert(rawKey: BigInt) : Unit = {
    val key = rawKey % modulo
    assert(key != myID)
    
    if (key < myID) {
      smaller += key
      if (smaller.size > L)
        smaller.dequeue()
    } else {
      bigger += key
      if (bigger.size > L)
        bigger.dequeue()
    }
  }
  
}