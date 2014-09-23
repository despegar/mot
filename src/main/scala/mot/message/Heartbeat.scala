package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer

case class Heartbeat() extends MessageBase {
  def writeToBuffer(writeBuffer: WriteBuffer): Unit = writeBuffer.put(MessageType.Heartbeat.id.toByte)
}

object Heartbeat {
  def factory(readBuffer: ReadBuffer, maxLength: Int) = Heartbeat()
}