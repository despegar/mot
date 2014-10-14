package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import mot.Util.ByteToBoolean
import mot.Util.BooleanToByte
import mot.BadDataException
import scala.collection.immutable
import java.nio.ByteBuffer

case class MessageFrame(
    respondable: Boolean,
    timeout: Int,
    override val attributes: immutable.Seq[(String, Array[Byte])], 
    override val bodyLength: Int,
    override val body: immutable.Seq[ByteBuffer]) extends MessageBase {

  def messageType = MessageType.Message
  
  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.Message.id.toByte)
    writeBuffer.put(respondable.toByte)
    writeBuffer.putInt(timeout)
    MessageBase.writeAttributes(writeBuffer, attributes)
    MessageBase.writeIntSizeByteMultiField(writeBuffer, bodyLength, body)
  }
  
  override def toString() = {
    val attrKeys = attributes.unzip._1
    val name = if (respondable) "request" else "message"
    s"$name timeout $timeout, attr [${attrKeys.mkString(",")}], length $bodyLength"
  }

}

object MessageFrame {

  def factory(readBuffer: ReadBuffer, maxLength: Int) = {
    val respondable = readBuffer.get().toBoolean
    val timeout = readBuffer.getInt()
    if (!respondable && timeout != 0)
      throw new BadDataException("Received non-zero timeout value for a non-respondable message: " + timeout)
    val attributes = MessageBase.readAttributes(readBuffer)
    val body = MessageBase.readIntSizeByteField(readBuffer, maxLength)
    MessageFrame(respondable, timeout, attributes, body.length, ByteBuffer.wrap(body) :: Nil /* use :: to avoid mutable builders */)
  }

}