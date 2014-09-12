package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import java.nio.charset.StandardCharsets

case class ServerHello(protocolVersion: Byte, name: String, maxLength: Int) extends MessageBase {

  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.ServerHello.id.toByte)
    writeBuffer.put(protocolVersion)
    MessageBase.writeByteSizeByteField(writeBuffer, name.getBytes(StandardCharsets.US_ASCII))
    writeBuffer.putInt(maxLength)
  }
  
  override def toString() = s"ServerHello(protocolVersion=$protocolVersion,maxLength=$maxLength)"

}

object ServerHello{

  def factory(readBuffer: ReadBuffer, maxLength: Int) = {
    val version = readBuffer.get()
    val server = new String(MessageBase.readByteSizeByteField(readBuffer), StandardCharsets.US_ASCII)
    val maxLength = readBuffer.getInt()
    ServerHello(version, server, maxLength)
  }

}