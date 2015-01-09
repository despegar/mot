package mot.protocol

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import scala.collection.immutable
import mot.util.Util
import mot.util.ByteArray

/**
 *  0                   1                   2                   3   
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 
 * ╭───────────────────────────────────────────────────────────────╮
 * ╎                                                               ╎
 * ╎                     Header (see Frame)                        ╎
 * ╎                                                               ╎
 * ├─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┤
 * │0│                    Reference (31 bits)                      │
 * ├─┴─────────────────────────────────────────────────────────────┤
 * ╎                                                               ╎
 * ╎              Attributes (see AttributeSupport)                ╎
 * ╎                                                               ╎
 * ├─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┤
 * ╎                                                               ╎
 * ╎                    Body (see BodySupport)                     ╎
 * ╎                                                               ╎
 * ╰───────────────────────────────────────────────────────────────╯
 */
case class ResponseFrame(
    val reference: Int,
    val attributes: immutable.Seq[(String, ByteArray)],
    val bodyLength: Int, 
    val body: immutable.Seq[ByteArray]
) extends Frame with AttributesSupport with BodySupport {

  def messageType = MessageTypes.Response
  
  val length =
    Util.bytes31 + // reference
    attributesLength() + 
    bodyLength
  
  def writeSpecific(writeBuffer: WriteBuffer) = {
    writeBuffer.put31(reference)
    writeAttributes(writeBuffer)
    writeBody(writeBuffer)
  }

  override def dump() = s"ref $reference"

}

object ResponseFrame extends FrameFactory[ResponseFrame] {

  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int) = {
    val requestReference = readBuffer.get31()
    val attributes = AttributesSupport.readAttributes(readBuffer)
    val body = BodySupport.readBody(readBuffer)
    val bodyParts = body :: Nil /* use :: to avoid mutable builders */
    ResponseFrame(requestReference, attributes, body.length, bodyParts)
  }

}