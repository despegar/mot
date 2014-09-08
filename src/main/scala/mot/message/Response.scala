package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import scala.collection.immutable
import java.nio.ByteBuffer

case class Response(
  requestReference: Int,
  attributes: immutable.Seq[(String, Array[Byte])],
  bodyParts: immutable.Seq[ByteBuffer]) extends MessageBase {

  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.Response.id.toByte)
    writeBuffer.putInt(requestReference)
    MessageBase.writeAttributes(writeBuffer, attributes)
    MessageBase.writeIntSizeByteMultiField(writeBuffer, bodyParts)
  }

  override def toString() = {
    val attrKeys = attributes.unzip._1
    s"Response(reqRef=$requestReference,attributes=[${attrKeys.mkString(",")}],bodySize=${bodyParts.map(_.limit).sum})"
  }

}

object Response {

  def factory(readBuffer: ReadBuffer, maxLength: Int) = {
    val requestReference = readBuffer.getInt()
    val attributes = MessageBase.readAttributes(readBuffer)
    val body = MessageBase.readIntSizeByteField(readBuffer, maxLength)
    Response(requestReference, attributes, ByteBuffer.wrap(body) :: Nil /* use :: to avoid mutable builders */)
  }

}