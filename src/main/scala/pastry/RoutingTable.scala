package pastry


import scala.collection.mutable.HashSet

import org.slf4j.{ Logger => Underlying }
import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.Logger

class RoutingTable(ID : BigInt) extends Config with Traversable[String] {

  val logger = Logger(LoggerFactory.getLogger("pastry"))

  val myID = ID
  val myIDStr = toBase(ID)
  
  val values = new HashSet[BigInt]
  val strValues = new HashSet[String]
  
  val table = Array.ofDim[String](exponent, base)
  
  
  // Initialize the array.
  for( y <- 1 to base; x <- 1 to exponent)
    table(x - 1)(y - 1) = "-"
  
  def foreach[U](f: String => U) = 
    for(row <- table)
      for(value <- row) value match {
        case "-" => // Skip!
        case valid : String => f(value) 
      }
        
        
  def dump() : Unit = {
    for( row <- table) {
      for(value <- row)
        printf(s"%${exponent}s ", value)
      println()
    }
  } 
    
  def location(keyStr : String) : (Int, Int) = {
    
    assert(keyStr != myIDStr, "Strings are equivalent.")
    
    val y = commonPrefix(keyStr, myIDStr) - 1
    val x = charToIndex(keyStr.charAt(y))
    
    return (y, x)
  }
  
  /**
   * The routing table is used and the message is forwarded 
   * to a node that shares a common prefix with the key by 
   * at least one more digit.
   */
  def computeNext(key: BigInt) : Option[BigInt] = {
    val keyStr = toBase(key)
    val (y, x) = location(keyStr)
    
    table(y)(x) match {
      case "-" => return None
      case _ => return Some( fromBase( table(y)(x) ) )
    }
  }
  
  
  def remove(key : BigInt) : Option[(Int, Int)] = {
    values -= key
    return removeStr(toBase(key))
  }
  
  
  def removeStr(keyStr : String) : Option[(Int, Int)] = {
    strValues -= keyStr
    
    val (y, x) = location(keyStr)
    logger.trace(myIDStr + ": " + "Removing " 
        + keyStr + " at row " + y + " column " + x)
    table(y)(x) match {
      case "-" => return None
      case _ =>
        table(y)(x) = "-"
        return Some((y, x))
    }
  }
  
  
  /**
   * Inefficient, but correct -- we're taking all the 
   * non-empty entries from the other routing table and 
   * inserting them into this one.
   */
  def steal(other: RoutingTable) : Unit =
    for (y <- 0 to exponent - 1)
      for (x <- 0 to base - 1)
        other.table(y)(x) match {
          case "-" => // Skip!
          case newEntry if (newEntry == myIDStr) => // Skip!
          case newEntry => insertStr(newEntry)
        }
  
  
  /**
   * Given a key, insert it in appropriate location in the table.
   * By default, if there is already a value stored at that location
   * in the table, it is not overwritten.
   */
  def insert(key : BigInt, overwrite : Boolean = false) : Option[(Int, Int)] = {
    values += key
    return insertStr(toBase(key), overwrite)
  }
  

  def insertStr(keyStr : String, overwrite : Boolean = false) : Option[(Int, Int)] = {
    assert(keyStr != myIDStr)
    
    strValues += keyStr
    
    val (y, x) = location(keyStr)
    logger.trace(myIDStr + ": " + "Inserting " 
        + keyStr + " at row " + y + " column " + x)
     
    (table(y)(x), overwrite) match {
      case (_, true) => 
        table(y)(x) = keyStr
        return Some((y, x))
      case ("-", _) =>
        table(y)(x) = keyStr
        return Some((y, x))
      case (_, _) =>
        return None
    }
    
  }
}
