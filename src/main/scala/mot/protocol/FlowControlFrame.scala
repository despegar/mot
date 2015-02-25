package mot.protocol

import mot.util.Util
import mot.buffer.WriteBuffer
import mot.buffer.ReadBuffer
import mot.util.Util.ByteToBoolean
import mot.util.Util.BooleanToByte

/**
 * Frame sent by clients to open or close a flow.
 * 
 * <pre>
 *  0                   1                   2                   3   
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 
 * ╭───────────────────────────────────────────────────────────────╮
 * ╎                                                               ╎
 * ╎                     Header (see Frame)                        ╎
 * ╎                                                               ╎
 * ├─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┤
 * │0│                    Flow ID (31 bits)                        │
 * ├─┼─┬─┬─┬─┬─┬─┬─┬───────────────────────────────────────────────╯
 * │ Open (0 or 1) │                                               
 * ╰───────────────╯
 * </pre>
 *
 * @see [[mot.ClientFlow]]
 * 
 */
case class FlowControlFrame(flowId: Int, open: Boolean) extends Frame {
  
  def messageType = MessageTypes.FlowControl
  
  def length = 
    Util.bytes31 + // flowId
    1 // open?
    
  def writeSpecific(writeBuffer: WriteBuffer) = {
    writeBuffer.put31(flowId)
    writeBuffer.put(open.toByte)
  }
 
  def dump() = s"flow $flowId, open $open"
  
}

object FlowControlFrame extends FrameFactory[FlowControlFrame] {
  
  def build(readBuffer: ReadBuffer, messageType: Byte, length: Int) = {
    val flowId = readBuffer.get31()
    val open = readBuffer.get().toBoolean
    FlowControlFrame(flowId, open)
  }
  
}