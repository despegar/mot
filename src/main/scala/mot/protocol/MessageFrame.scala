package mot.protocol

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import mot.util.ByteArray

/**
 *  0                   1                   2                   3   
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 
 * ╭───────────────────────────────────────────────────────────────╮
 * ╎                                                               ╎
 * ╎                     Header (see Frame)                        ╎
 * ╎                                                               ╎
 * ├───────────────────────────────────────────────────────────────┤
 * ╎                                                               ╎
 * ╎              Attributes (see AttributeSupport)                ╎
 * ╎                                                               ╎
 * ├─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┤
 * ╎                                                               ╎
 * ╎                    Body (see BodySupport)                     ╎
 * ╎                                                               ╎
 * ╰───────────────────────────────────────────────────────────────╯
 */
case class MessageFrame(
    val attributes: Seq[(String, ByteArray)], 
    val bodyLength: Int,
    val body: Seq[ByteArray]
) extends Frame with AttributesSupport with BodySupport {

  def messageType = MessageTypes.Message
  
  val length = 
    attributesLength() +
    bodyLength
  
  def writeSpecific(writeBuffer: WriteBuffer) = {
    writeAttributes(writeBuffer)
    writeBody(writeBuffer)
  }
  
  def dump() = ""
  
}

object MessageFrame extends FrameFactory[MessageFrame] {

  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int) = {
    val attributes = AttributesSupport.readAttributes(readBuffer)
    val body = BodySupport.readBody(readBuffer)
    val bodyParts = body :: Nil /* use :: to avoid mutable builders */
    MessageFrame(attributes, body.length, bodyParts)
  }

}