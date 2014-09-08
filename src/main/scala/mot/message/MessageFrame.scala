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
    attributes: immutable.Seq[(String, Array[Byte])], 
    bodyParts: immutable.Seq[ByteBuffer]) extends MessageBase {

  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.Message.id.toByte)
    writeBuffer.put(respondable.toByte)
    writeBuffer.putInt(timeout)
    MessageBase.writeAttributes(writeBuffer, attributes)
    MessageBase.writeIntSizeByteMultiField(writeBuffer, bodyParts)
  }
  
  override def toString() = {
    val attrKeys = attributes.unzip._1
    val bodySize = bodyParts.map(_.limit).sum
    s"MessageFrame(respondable=$respondable,timeout=$timeout,attributes=[${attrKeys.mkString(",")}],bodySize=$bodySize)"
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
    MessageFrame(respondable, timeout, attributes, ByteBuffer.wrap(body) :: Nil /* use :: to avoid mutable builders */)
  }

}