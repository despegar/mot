package mot.buffer

import java.nio.ByteBuffer
import java.io.InputStream
import java.io.EOFException

/**
 * A buffer for reading bytes from an InputStream. At any time, the buffer is partitioned into three sections,
 * from left to right: consumed, pending and free.
 * Consumed bytes were read from the InputStream and also passed to the application. This data can be deleted.
 * Pending bytes were read from the InputStream and not passed to the application.
 * Free space at the end of the buffer is not occupied by useful bytes, new read from the InputStream go here.  
 */
class ReadBuffer(val is: InputStream, val bufferSize: Int) {

  val array = Array.ofDim[Byte](bufferSize)

  private var highPosition = 0
  private var lowPosition = 0

  def consumed() = lowPosition
  def pending() = highPosition - lowPosition
  def freeSpace() = bufferSize - highPosition

  def hasPending() = highPosition > lowPosition
  def hasFreeSpace() = bufferSize > highPosition
  def isCompressed() = lowPosition == 0

  @volatile private var _readCount = 0L
  @volatile private var _fullReadCount = 0L
  @volatile private var _bytesCount = 0L
  
  def readCount() = _readCount
  def fullReadCount() = _fullReadCount
  def bytesCount() = _bytesCount
  
  def clear() {
    lowPosition = 0
    highPosition = 0
  }

  def compress() {
    val length = pending()
    if (length > 0) {
      System.arraycopy(array, lowPosition, array, 0, length)
      highPosition = length
      lowPosition = 0
    } else {
      clear()
    }
  }

  def fill() {
    if (!isCompressed)
      compress()
    val bytesRead = is.read(array, highPosition, freeSpace)
    if (bytesRead == -1)
      throw new EOFException("EOF reading from input stream")
    highPosition += bytesRead
    _bytesCount += bytesRead
    _readCount += 1
    if (!hasFreeSpace)
      _fullReadCount += 1
  }

  def transferToByteBuffer(targetBuffer: ByteBuffer, length: Int) {
    var bytesTransfered = 0
    while (bytesTransfered < length) {
      if (!hasPending)
        fill()
      val bytesToTransfer = math.min(pending, length - bytesTransfered)
      targetBuffer.put(array, lowPosition, bytesToTransfer)
      lowPosition += bytesToTransfer
      bytesTransfered += bytesToTransfer
    }
  }

  def get() = {
    if (!hasPending)
      fill()
    val res = array(lowPosition)
    lowPosition += 1
    res
  }

  /*
   * Use network (big-endian) byte order
   */
  def getShort() = {
    val shortBytes = 2
    while (pending < shortBytes)
      fill()
    val res = ReadBuffer.getShortBigEndian(array, lowPosition)
    lowPosition += shortBytes
    res
  }
  
  /*
   * Use network (big-endian) byte order
   */
  def getInt() = {
    val intBytes = 4
    while (pending < intBytes)
      fill()
    val res = ReadBuffer.getIntBigEndian(array, lowPosition)
    lowPosition += intBytes
    res
  }
  
  override def toString() = {
    val hexContents = array.map("%02X" format _).mkString(",")
    val asciiContents = array.map(b => if (Character.isISOControl(b)) '.' else b.toChar).mkString
    s"ReadBuffer(lowPosition=$lowPosition,highPosition=$highPosition,length=${array.length}," +
      s"hexContents=$hexContents,asciiContents=$asciiContents)"
  }

}

object ReadBuffer {
  
  def getShortBigEndian(array: Array[Byte], pos: Int) = makeShort(array(pos), array(pos + 1))
  
  def getIntBigEndian(array: Array[Byte], pos: Int) = 
    makeInt(array(pos), array(pos + 1), array(pos + 2), array(pos + 3))
    
  def makeShort(b1: Byte, b0: Byte) = ((b1 << 8) | (b0 & 0xff)).toShort
  def makeInt(b3: Byte, b2: Byte, b1: Byte, b0: Byte) = (b3 << 24) | ((b2 << 16) & 0xffffff) | ((b1 << 8) & 0xffff) | (b0 & 0xff)
  
}
