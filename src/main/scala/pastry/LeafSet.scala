package pastry

import scala.collection.mutable.PriorityQueue

class LeafSet(ID : BigInt) extends Config with Traversable[BigInt] {
  
  val myID : BigInt = (ID % modulo) + modulo
  val myIDStr = toBase(ID % modulo)
  
  val L : Int = 4
  var left = scala.collection.mutable.PriorityQueue[Int]()(new scala.math.Ordering[Int] {
      def compare(x: Int, y: Int) = (y - x)
  })
    
  var right = scala.collection.mutable.PriorityQueue[Int]()
  
  def values : PriorityQueue[BigInt] =
    left.map { x => x % modulo } ++ 
    right.map { x => x % modulo }
  
  
  override def equals (other: Any) = other match {
    case otherStruct : LeafSet => 
      myID == otherStruct.myID &&
      left.toArray.deep == otherStruct.left.toArray.deep &&
      right.toArray.deep == otherStruct.right.toArray.deep
    case _ => false
  }
  
  
  def leftNeighStr = toBase(leftNeigh)
  def leftNeigh : Int = {
    val l = left.clone()
    return l.dequeueAll.iterator.drop(left.size - 1).next()
  }
  
  
  def rightNeighStr = toBase(rightNeigh)
  def rightNeigh : Int = {
    val r = right.clone()
    return r.dequeueAll.iterator.drop(right.size - 1).next()
  }
  
  
  def foreach[U](f: BigInt => U) = 
    for(value <- values)
      f(value)
  
  
  override def clone() : LeafSet = {
    val newLS = new LeafSet(myID)
    newLS.left = left.clone()
    newLS.right = right.clone()
    return newLS
  }
  
  
  def closest(rawKey: BigInt) : Option[BigInt] = {
    val key = rawKey % modulo
    assert(key % modulo != myID % modulo)
    
    if (left.isEmpty && right.isEmpty)
      return None
    
    return Some(values.minBy(v => (v - key).abs))
  }
  
  
  def steal(other: LeafSet) : Unit =
    for(value <- other.values) value match {
      case same : BigInt if same % modulo == myID % modulo =>
      case other : BigInt => this.insert(other)
      case _ => internalErrorF
    }
  
  
  def addTo(col: PriorityQueue[Int], value : Int) = {
      if (!col.exists(x => x == value))
        col.enqueue(value)
  }
  
  def insert(rawKey: BigInt) : Unit = {
    val key = (rawKey % modulo) + modulo
    assert(key % modulo != myID % modulo)

    
    key < myID match {
      case true =>
        addTo(left, key.toInt)
        addTo(right, (key + modulo).toInt)
        
        if (myIDStr == "012") {
          println("\t Inserting smaller " + toBase(key % modulo) + " -- " + key)
          println("\t " + left)
          println("\t " + toBase(left.last))
        }
        
      case false =>
        addTo(right, key.toInt)
        addTo(left, (key - modulo).toInt)
        
        if (myIDStr == "012") {
          println("\t Inserting bigger " + toBase(key % modulo) + " -- " + (key - modulo))
          println("\t " + left)
          println("\t " + toBase(left.last))
        }
    }
    
    left = left.result()
    right = right.result()
    
    while(left.size > L) {
      left.dequeue()
    }
      
    while(right.size > L) {
      right.dequeue()
    }
    
  }
  
}
