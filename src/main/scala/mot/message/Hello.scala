package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import java.nio.charset.StandardCharsets
import scala.collection.immutable
import scala.collection.mutable.HashMap
import mot.BadDataException

case class Hello(override val attributes: immutable.Seq[(String, Array[Byte])]) extends MessageBase {

  def messageType = MessageType.Hello
  
  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.Hello.id.toByte)
    MessageBase.writeAttributes(writeBuffer, attributes)
  }

  override def toString() = {
    val attrStr = for ((key, value) <- attributes) yield key + "=" + value
    s"hello attr [${attrStr.mkString(",")}]"
  }
  
  def attributesAsStringMap() = {
    val map = new HashMap[String, String]
    map.sizeHint(attributes.size)
    for ((key, value) <- attributes) {
      val previous = map.put(key, new String(value, StandardCharsets.US_ASCII))
      if (previous.isDefined)
        throw new BadDataException("Duplicated attribute key: " + key)
    }
    map.toMap
  }

}

object Hello {

  val versionKey = "version"
  val clientNameKey = "client-name"
  val serverNameKey = "server-name"
  val maxLengthKey = "max-length"
    
  def fromMap(map: Map[String, String]) =
    Hello(map.mapValues(_.getBytes(StandardCharsets.US_ASCII)).to[immutable.Seq])  
  
  def factory(readBuffer: ReadBuffer, maxLength: Int) = Hello(MessageBase.readAttributes(readBuffer))

  def getAttribute(attributes: Map[String, String], key: String) = {
    attributes.get(key).getOrElse(throw new BadDataException("Missing attribute in message: " + key))
  }
  
  def getIntAttribute(attributes: Map[String, String], key: String) = {
    val strValue = getAttribute(attributes, key)
    try {
      strValue.toInt
    } catch {
      case e: NumberFormatException => throw new BadDataException(strValue + "is not a number")
    }
  }
  
}