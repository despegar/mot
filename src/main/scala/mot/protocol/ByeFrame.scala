package mot.protocol

import mot.buffer.WriteBuffer
import mot.buffer.ReadBuffer

/**
 * Empty frame that signals the normal finalization of a connection. Nothing is sent or received after this frame.
 */
case class ByeFrame() extends Frame {

  def messageType = MessageTypes.Bye
  def length = 0

  def writeSpecific(writeBuffer: WriteBuffer): Unit = {}
  def dump = ""

}

object ByeFrame extends FrameFactory[ByeFrame] {
  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int) = ByeFrame()
}