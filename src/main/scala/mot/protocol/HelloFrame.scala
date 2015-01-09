package mot.protocol

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import java.nio.charset.StandardCharsets.US_ASCII
import scala.collection.immutable
import scala.collection.mutable.HashMap
import mot.util.ByteArray

/**
 * First frame send by both parties in every connection. 
 * 
 *  0                   1                   2                   3   
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 
 * ╭───────────────────────────────────────────────────────────────╮
 * ╎                                                               ╎
 * ╎                     Header (see Frame)                        ╎
 * ╎                                                               ╎
 * ├───────────────────────────────────────────────────────────────┤
 * ╎                                                               ╎
 * ╎              Attributes (see AttributeSupport)                ╎
 * ╎                                                               ╎
 * ╰───────────────────────────────────────────────────────────────╯
 */
case class HelloFrame(val attributes: immutable.Seq[(String, ByteArray)]) extends Frame with AttributesSupport {

  def messageType = MessageTypes.Hello
  val length = attributesLength()
  
  def writeSpecific(writeBuffer: WriteBuffer) = writeAttributes(writeBuffer)

  override def dump() = {
    val attrStr = for ((key, value) <- attributes) yield key + "=" + value.asString(US_ASCII)
    s"{${attrStr.mkString(",")}}"
  }
  
  def attributesAsStringMap() = {
    val map = new HashMap[String, String]
    map.sizeHint(attributes.size)
    for ((key, value) <- attributes) {
      val previous = map.put(key, value.asString(US_ASCII))
      if (previous.isDefined)
        throw new ProtocolSemanticException("Duplicated attribute key: " + key)
    }
    map.toMap
  }

}

object HelloFrame extends FrameFactory[HelloFrame] {
    
  def fromMap(map: Map[String, String]) = 
    HelloFrame(map.mapValues(a => ByteArray(a.getBytes(US_ASCII))).to[immutable.Seq])  
  
  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int) = 
    HelloFrame(AttributesSupport.readAttributes(readBuffer))

  def getAttribute(attributes: Map[String, String], key: String) =
    attributes.get(key).getOrElse(throw new ProtocolSemanticException("Missing attribute in message: " + key))
  
  def getIntAttribute(attributes: Map[String, String], key: String) = {
    val strValue = getAttribute(attributes, key)
    try {
      strValue.toInt
    } catch {
      case e: NumberFormatException => throw new ProtocolSemanticException(strValue + " is not a number")
    }
  }
  
}