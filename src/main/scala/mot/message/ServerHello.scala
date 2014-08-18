package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer

case class ServerHello(protocolVersion: Byte, maxLength: Short) extends MessageBase {

  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.ServerHello.id.toByte)
    writeBuffer.put(protocolVersion)
    writeBuffer.putShort(maxLength)
  }
  
   override def toString() = s"ServerHello(protocolVersion=$protocolVersion,maxLength=$maxLength)"

}

object ServerHello{

  def factory(readBuffer: ReadBuffer, maxLength: Int) = {
    val version = readBuffer.get()
    val maxLength = readBuffer.getShort()
    ServerHello(version, maxLength)
  }

}