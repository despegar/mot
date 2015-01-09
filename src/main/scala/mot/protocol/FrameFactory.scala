package mot.protocol

import mot.buffer.ReadBuffer

trait FrameFactory[+T <: Frame] {
  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int): T
}