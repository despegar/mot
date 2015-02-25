package mot.protocol

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import com.typesafe.scalalogging.slf4j.StrictLogging
import mot.buffer.LimitOverflowException
import java.io.OutputStream
import mot.buffer.EncodingException

/**
 *  Base trait for all protocol frames.
 * 
 * <pre>
 *  0                   1                   2                   3   
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 
 * ╭─┬─┬─┬─┬─┬─┬─┬─╮
 * │    Type (8)   │
 * ├─┬─┬─┬─┬─┬─┬─┬─┼─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─╮
 * │0│                 Message Length (31 bits)                    │
 * ├─┴─────────────────────────────────────────────────────────────┤
 * ╎                                                               ╎
 * ╎                     Data (see sub types)                      ╎
 * ╎                                                               ╎
 * ╰───────────────────────────────────────────────────────────────╯
 * </pre>
 */
trait Frame {

  def messageType: Byte
  def length: Int

  def write(writeBuffer: WriteBuffer) = {
    writeBuffer.put(messageType)
    writeBuffer.put31(length)
    writeSpecific(writeBuffer)
  }

  def writeSpecific(writeBuffer: WriteBuffer): Unit
  def dump(): String
  
}

object Frame extends StrictLogging {

  def read(readBuffer: ReadBuffer, maxLength: Int) = {
    val messageType = readBuffer.get
    val length = readBuffer.get31()
    if (length > maxLength)
      throw ProtocolSyntaxException(s"Message reported length ($length) bigger than maximum allowed length ($maxLength)")
    val factory = fromMessageType(messageType)
    readBuffer.setLimit(length)
    try {
      factory.build(readBuffer, messageType, length)
    } catch {
      case e: LimitOverflowException => 
        throw ProtocolSyntaxException(s"message longer than advertied limit ($length)", e)
      case e: EncodingException => 
        throw ProtocolSyntaxException(e.getMessage, e)
    } finally {
      readBuffer.resetLimit()
    }
  }

  def fromMessageType(messageType: Byte): FrameFactory[Frame] = messageType match {
    case 0 => HelloFrame
    case 1 => HeartbeatFrame
    case 2 => RequestFrame
    case 3 => ResponseFrame
    case 4 => MessageFrame
    case 5 => FlowControlFrame
    case 6 => ByeFrame
    case 7 => ResetFrame
    case _ => UnknownFrame
  }

}