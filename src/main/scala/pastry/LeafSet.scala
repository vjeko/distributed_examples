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
    val col = left.clone().dequeueAll
    return col.iterator.drop(col.size - 1).next()
  }
  
  
  def rightNeighStr = toBase(rightNeigh)
  def rightNeigh : Int = {
    val col = right.clone().dequeueAll
    return col.iterator.drop(col.size - 1).next()
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
  
  
  def insert(rawKey: BigInt) : Unit = {
    val key = (rawKey % modulo) + modulo
    assert(key % modulo != myID % modulo)

    
    key < myID match {
      case true =>
        left.enqueue(key.toInt)
        right.enqueue((key + modulo).toInt)
        
      case false =>
        right.enqueue(key.toInt)
        left.enqueue((key - modulo).toInt)
    }
    
    while(left.size > L) {
      left.dequeue()
    }
      
    while(right.size > L) {
      right.dequeue()
    }
    
  }
  
}
