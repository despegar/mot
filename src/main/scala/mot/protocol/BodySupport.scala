package mot.protocol

import java.io.OutputStream
import mot.buffer.WriteBuffer
import mot.buffer.ReadBuffer
import mot.util.ByteArray

/**
 * Frames that mix this trait can add a body, which is a byte array at the end of the frame.
 */
trait BodySupport {
  
  val body: Seq[ByteArray]

  def dumpBody(os: OutputStream, maxLength: Int) = {
    if (body.size > 0) {
      var remaining = maxLength
      for (buffer <- body) {
        val show = math.min(remaining, buffer.length)
        os.write(buffer.array, buffer.offset, show)
        remaining -= show
      }
      os.write('\n')
    }
  }
  
  def writeBody(writeBuffer: WriteBuffer) = {
    var it = body.iterator
    while (it.hasNext) {
      val b = it.next()
      writeBuffer.put(b)
    }
  }
  
}

object BodySupport {
  def readBody(readBuffer: ReadBuffer) = readBuffer.getByteArray(readBuffer.remainingLimit)
}