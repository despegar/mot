package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import mot.Util.ByteToBoolean
import mot.Util.BooleanToByte
import mot.BadDataException

case class MessageFrame(
    respondable: Boolean,
    timeout: Int,
    attributes: Map[String, Array[Byte]], 
    bodyParts: Seq[Array[Byte]]) extends MessageBase {

  val bodySize = bodyParts.map(_.length).sum
  
  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.Message.id.toByte)
    writeBuffer.put(respondable.toByte)
    writeBuffer.putInt(timeout)
    MessageBase.writeAttributes(writeBuffer, attributes)
    MessageBase.writeIntSizeByteMultiField(writeBuffer, bodyParts)
  }
  
  override def toString() = 
    s"MessageFrame(respondable=$respondable,timeout=$timeout,attributes=[${attributes.keys.mkString(",")}],bodySize=$bodySize)"

}

object MessageFrame {

  def factory(readBuffer: ReadBuffer, maxLength: Int) = {
    val respondable = readBuffer.get().toBoolean
    val timeout = readBuffer.getInt()
    if (!respondable && timeout != 0)
      throw new BadDataException("Received non-zero timeout value for a non-respondable message: " + timeout)
    val attributes = MessageBase.readAttributes(readBuffer)
    val body = MessageBase.readIntSizeByteField(readBuffer, maxLength)
    // TODO: Ver qué hacer con los atributos repetidos
    MessageFrame(respondable, timeout, attributes.toMap, Seq(body))
  }

}