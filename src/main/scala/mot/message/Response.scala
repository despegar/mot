package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import scala.collection.immutable
import java.nio.ByteBuffer

case class Response(
  requestReference: Int,
  override val attributes: immutable.Seq[(String, Array[Byte])],
  override val bodyLength: Int, 
  override val body: immutable.Seq[ByteBuffer]) extends MessageBase {

  def messageType = MessageType.Response
  
  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.Response.id.toByte)
    writeBuffer.putInt(requestReference)
    MessageBase.writeAttributes(writeBuffer, attributes)
    MessageBase.writeIntSizeByteMultiField(writeBuffer, bodyLength, body)
  }

  override def toString() = {
    val attrKeys = attributes.unzip._1
    s"response ref $requestReference, attr [${attrKeys.mkString(",")}], length ${body.map(_.limit).sum}"
  }

}

object Response {

  def factory(readBuffer: ReadBuffer, maxLength: Int) = {
    val requestReference = readBuffer.getInt()
    val attributes = MessageBase.readAttributes(readBuffer)
    val body = MessageBase.readIntSizeByteField(readBuffer, maxLength)
    Response(requestReference, attributes, body.length, ByteBuffer.wrap(body) :: Nil /* use :: to avoid mutable builders */)
  }

}