package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import java.nio.charset.StandardCharsets

case class ClientHello(protocolVersion: Byte, sender: String, maxLength: Short) extends MessageBase {
  
  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.ClientHello.id.toByte)
    writeBuffer.put(protocolVersion)
    MessageBase.writeByteSizeByteField(writeBuffer, sender.getBytes(StandardCharsets.US_ASCII))
    writeBuffer.putShort(maxLength)
  }
  
  override def toString() = s"ClientHello(protocolVersion=$protocolVersion,sender=$sender,maxLength=$maxLength)"
  
}

object ClientHello {
  
  def factory(readBuffer: ReadBuffer, maxLength: Int) = {
    val version = readBuffer.get()
    val sender = new String(MessageBase.readByteSizeByteField(readBuffer), StandardCharsets.US_ASCII)
    val maxLength = readBuffer.getShort()
    ClientHello(version, sender, maxLength)
  }
  
}

