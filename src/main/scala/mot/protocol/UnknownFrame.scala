package mot.protocol

import mot.buffer.WriteBuffer
import mot.buffer.ReadBuffer

/**
 * Special frame type, which represents an unknown received frame. It is never written.
 */
case class UnknownFrame(messageType: Byte, length: Int) extends Frame {
  def writeSpecific(writeBuffer: WriteBuffer) = throw new UnsupportedOperationException
  def dump() = ""
}

object UnknownFrame extends FrameFactory[UnknownFrame] {
  
  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int) = {
    readBuffer.discard(length)
    UnknownFrame(messageType, length)
  }
  
}