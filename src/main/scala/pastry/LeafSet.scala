package pastry

import scala.collection.mutable.SortedSet

class LeafSet(ID : BigInt) extends Config with Traversable[BigInt] {
  
  val myID : BigInt = ID
  val myIDStr = toBase(ID)
  
  val L : Int = 4
  var smaller = SortedSet[BigInt]()
  var bigger = SortedSet[BigInt]()
  
  
  def values : SortedSet[BigInt] = smaller ++ bigger
  
  
  def foreach[U](f: BigInt => U) = 
    for(value <- smaller ++ bigger)
      f(value)
  
      
  def smallest[A](set: SortedSet[A]) : A = {
    return set.take(1).head
  }
  
  
  def biggest[A](set: SortedSet[A]) : A = {
    return set.takeRight(1).head
  }
  
  
  def closest(key: BigInt) : Option[BigInt] = {
    assert(key != myID)
    
    key < myID match {
      
      case true =>
        val set = smaller
        
        if (set.isEmpty || key < smallest(set))
          return None
          
        return Some(set.minBy(v => (v - key).abs))
        
      case false =>
        val set = bigger

        if (set.isEmpty || key > biggest(set))
          return None
          
        return Some(set.minBy(v => (v - key).abs))
    }

  }
  
  
  
  def steal(other: LeafSet) : Unit =
    for(value <- other.bigger ++ other.smaller) value match {
      case same : BigInt if same == myID =>
      case other : BigInt => this.insert(other)
      case _ => internalErrorF
    }
   
  
  
  
  def insert(key: BigInt) : Unit = {
    assert(key != myID)
    
    if (key < myID) {
      smaller += key
      if (smaller.size > L)
        smaller = smaller.drop(1)
    } else {
      bigger += key
      if (bigger.size > L)
        bigger = bigger.dropRight(1)
    }
  }
  
}