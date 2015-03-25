package mot.buffer

import java.io.InputStream
import java.io.EOFException
import mot.util.Util
import mot.util.ByteArray
import java.util.concurrent.atomic.AtomicLong
import mot.util.Util.RichAtomicLong

/**
 * A buffer for reading bytes from an InputStream. At any time, the buffer is partitioned into three sections,
 * from left to right: consumed, pending and free.
 * Consumed bytes were read from the InputStream and also passed to the application. This data can be deleted.
 * Pending bytes were read from the InputStream and not passed to the application.
 * Free space at the end of the buffer is not occupied by useful bytes, new read from the InputStream go here.  
 */
class ReadBuffer(val is: InputStream, val bufferSize: Int) {

  import ReadBuffer._
  
  val array = Array.ofDim[Byte](bufferSize)

  private var highPosition = 0
  private var lowPosition = 0

  def consumed() = lowPosition
  def pending() = highPosition - lowPosition
  def freeSpace() = bufferSize - highPosition

  def hasPending() = highPosition > lowPosition
  def hasFreeSpace() = bufferSize > highPosition
  def isCompressed() = lowPosition == 0

  private val _readCount = new AtomicLong
  private val _fullReadCount = new AtomicLong
  private val _bytesCount = new AtomicLong
  
  def readCount() = _readCount.get
  def fullReadCount() = _fullReadCount.get
  def bytesCount() = _bytesCount.get
  
  private var processedCount = 0L
  private var processedMark = -1L
  private var limit = -1
  
  def setLimit(limit: Int): Unit = {
    this.limit = limit
    processedMark = processedCount
  }
  
  def resetLimit() = {
    if (processedMark == -1)
      throw new IllegalStateException("No previous limit")
    val res = remainingLimit
    processedMark = -1
    res
  }
  
  def hasLimit() = processedMark != -1
  
  def remainingLimit() = {
    if (processedMark == -1)
      throw new IllegalStateException("No previous limit")
    limit - (processedCount - processedMark).toInt
  }
  
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

  private def fill() {
    if (!isCompressed)
      compress()
    val bytesRead = is.read(array, highPosition, freeSpace)
    if (bytesRead == -1)
      throw new EOFException("EOF reading from input stream")
    highPosition += bytesRead
    _bytesCount.lazyAdd(bytesRead)
    _readCount.lazyIncrement()
    if (!hasFreeSpace)
      _fullReadCount.lazyIncrement()
  }

  def getByteArray(length: Int): ByteArray = {
    val targetBuffer = ByteArray.allocate(length)
    if (hasLimit && length > remainingLimit)
      throw new LimitOverflowException(limit)
    var bytesTransfered = 0
    while (bytesTransfered < length) {
      if (!hasPending)
        fill()
      val bytesToTransfer = math.min(pending, length - bytesTransfered)
      System.arraycopy(array, lowPosition, targetBuffer.array, targetBuffer.offset + bytesTransfered, bytesToTransfer)
      lowPosition += bytesToTransfer
      bytesTransfered += bytesToTransfer
    }
    processedCount += bytesTransfered
    targetBuffer
  }

  def discard(length: Int) {
    if (hasLimit && length > remainingLimit)
      throw new LimitOverflowException(limit)
    var bytesDiscarded = 0
    while (bytesDiscarded < length) {
      if (!hasPending)
        fill()
      val bytesToDiscard = math.min(pending, length - bytesDiscarded)
      lowPosition += bytesToDiscard
      bytesDiscarded += bytesToDiscard
    }
    processedCount += bytesDiscarded    
  }
  
  def get() = {
    if (hasLimit && remainingLimit < 1)
      throw new LimitOverflowException(limit)
    if (!hasPending)
      fill()
    val res = array(lowPosition)
    lowPosition += 1
    processedCount += 1
    res
  }

  def get8() = {
    val bytes = 1
    if (hasLimit && remainingLimit < bytes)
      throw new LimitOverflowException(limit)
    while (pending < bytes)
      fill()
    val res = make8(array(lowPosition))
    lowPosition += bytes
    processedCount += bytes
    res
  }  
  
  def get16() = {
    val bytes = Util.bytes16
    if (hasLimit && remainingLimit < bytes)
      throw new LimitOverflowException(limit)
    while (pending < bytes)
      fill()
    val res = make16(array(lowPosition), array(lowPosition + 1))
    lowPosition += bytes
    processedCount += bytes
    res
  }  
  
  def get24() = {
    val bytes = Util.bytes24
    if (hasLimit && remainingLimit < bytes)
      throw new LimitOverflowException(limit)
    while (pending < bytes)
      fill()
    val res = make24(array(lowPosition), array(lowPosition + 1), array(lowPosition + 2))
    lowPosition += bytes
    processedCount += bytes
    res
  }
  
  def get31() = {
    val bytes = Util.bytes31
    if (hasLimit && remainingLimit < bytes)
      throw new LimitOverflowException(limit)
    while (pending < bytes)
      fill()
    val res = make31(array(lowPosition), array(lowPosition + 1), array(lowPosition + 2), array(lowPosition + 3))
    if (res < 0)
      throw new EncodingException("Highest order bit cannot be set in 31 bit integers")      
    lowPosition += bytes
    processedCount += bytes
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
  
  // Methods below read big-endian (network) unsigned integers
    
  def make8(b0: Byte) = (b0 & 0xff).toShort
  
  def make16(b1: Byte, b0: Byte) = 
    (b1 << 8)  & 0xffff | 
    b0 & 0xff
  
  def make24(b2: Byte, b1: Byte, b0: Byte) = 
    (b2 << 16) & 0xffffff | 
    (b1 << 8) & 0xffff | 
    b0 & 0xff
  
  def make31(b3: Byte, b2: Byte, b1: Byte, b0: Byte) = 
    (b3 << 24) | 
    (b2 << 16) & 0xffffff | 
    (b1 << 8) & 0xffff | 
    b0 & 0xff  
    
}
