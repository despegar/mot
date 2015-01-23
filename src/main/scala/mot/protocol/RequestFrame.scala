package mot.protocol

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
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
 * │0│                   Request ID (31 bits)                      │
 * ├─┼─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┤
 * │0│                    Flow ID (31 bits)                        │
 * ├─┼─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┤
 * │0│                    Timeout (31 bits)                        │
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
case class RequestFrame(
    val requestId: Int,
    val flowId: Int,
    val timeout: Int,
    val attributes: Seq[(String, ByteArray)], 
    val bodyLength: Int,
    val body: Seq[ByteArray]
) extends Frame with AttributesSupport with BodySupport {

  def messageType = MessageTypes.Request
  
  val length =
    Util.bytes31 + // requestId
    Util.bytes31 + // flowId
    Util.bytes31 + // timeout
    attributesLength() + 
    bodyLength
  
  def writeSpecific(writeBuffer: WriteBuffer) = {
    writeBuffer.put31(requestId)
    writeBuffer.put31(flowId)
    writeBuffer.put31(timeout)
    writeAttributes(writeBuffer)
    writeBody(writeBuffer)
  }
  
  def dump() = s"ref $requestId, flow $flowId, timeout $timeout"
  
}

object RequestFrame extends FrameFactory[RequestFrame] {

  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int) = {
    val requestId = readBuffer.get31()
    val flowId = readBuffer.get31()
    val timeout = readBuffer.get31()
    val attributes = AttributesSupport.readAttributes(readBuffer)
    val body = BodySupport.readBody(readBuffer)
    val bodyParts = body :: Nil /* use :: to avoid mutable builders */
    RequestFrame(requestId, flowId, timeout, attributes, body.length, bodyParts)
  }

}