package mot.protocol

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer

/**
 * Empty frame that keeps the connection with some activity when there are not other kind of frames.
 */
case class HeartbeatFrame() extends Frame {
  
  def messageType = MessageTypes.Heartbeat
  def length = 0
  
  def writeSpecific(writeBuffer: WriteBuffer): Unit = {}
  def dump = ""
  
}

object HeartbeatFrame extends FrameFactory[HeartbeatFrame] {
  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int) = HeartbeatFrame()
}