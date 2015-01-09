package mot.protocol

import mot.buffer.WriteBuffer
import mot.buffer.ReadBuffer
import java.nio.charset.StandardCharsets.US_ASCII
import scala.collection.immutable
import mot.util.ByteArray

/** 
 * Frame that is sent in the event of an abnormal connection termination. The body can have a reason. Nothing is sent 
 * or received after this frame.
 * 
 *  0                   1                   2                   3   
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 
 * ╭───────────────────────────────────────────────────────────────╮
 * ╎                                                               ╎
 * ╎                     Header (see Frame)                        ╎
 * ╎                                                               ╎
 * ├─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┤
 * ╎                                                               ╎
 * ╎                    Body (see BodySupport)                     ╎
 * ╎                                                               ╎
 * ╰───────────────────────────────────────────────────────────────╯
 */
case class ResetFrame(error: String) extends Frame with BodySupport {
  
  val body = ByteArray(error.getBytes(US_ASCII)) :: Nil
  val bodyLength = body.length
  
  def messageType = MessageTypes.Reset
  def length = bodyLength
  
  def writeSpecific(writeBuffer: WriteBuffer): Unit = writeBody(writeBuffer)
  def dump = ""
  
}

object ResetFrame extends FrameFactory[ResetFrame] {
  
  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int) = {
    val body = BodySupport.readBody(readBuffer)
    ResetFrame(body.asString(US_ASCII))
  }
  
}
