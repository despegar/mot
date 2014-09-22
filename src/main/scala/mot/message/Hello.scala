package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import java.nio.charset.StandardCharsets
import scala.collection.immutable

case class Hello(protocolVersion: Byte, attributes: Map[String, String]) extends MessageBase {

  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.Hello.id.toByte)
    writeBuffer.put(protocolVersion)
    MessageBase.writeAttributes(writeBuffer, attributes.mapValues(_.getBytes(StandardCharsets.US_ASCII)).to[immutable.Seq])
  }

  override def toString() = {
    val attrStr = for ((key, value) <- attributes) yield key + "=" + value
    s"Hello(protocolVersion=$protocolVersion,${attrStr.mkString(",")})"
  }

}

object Hello {

  def factory(readBuffer: ReadBuffer, maxLength: Int) = {
    val version = readBuffer.get()
    val attributes = MessageBase.readAttributesAsStringMap(readBuffer)
    Hello(version, attributes)
  }

}