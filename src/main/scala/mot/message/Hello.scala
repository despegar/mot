package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import java.nio.charset.StandardCharsets
import scala.collection.immutable

case class Hello(attributes: Map[String, String]) extends MessageBase {

  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.Hello.id.toByte)
    MessageBase.writeAttributes(writeBuffer, attributes.mapValues(_.getBytes(StandardCharsets.US_ASCII)).to[immutable.Seq])
  }

  override def toString() = {
    val attrStr = for ((key, value) <- attributes) yield key + "=" + value
    s"Hello(${attrStr.mkString(",")})"
  }

}

object Hello {

  val versionKey = "version"
  val clientNameKey = "client-name"
  val serverNameKey = "server-name"
  val maxLengthKey = "max-length"
    
  def factory(readBuffer: ReadBuffer, maxLength: Int) = Hello(MessageBase.readAttributesAsStringMap(readBuffer))

}