package mot.buffer

import java.io.OutputStream

class WriteBuffer(val os: OutputStream, val bufferSize: Int) {

  val array = Array.ofDim[Byte](bufferSize)
  
  private var position = 0
  
  @volatile private var _bytesCount = 0L
  @volatile private var _writeCount = 0L
  @volatile private var _directWriteCount = 0L
  @volatile private var _fullWriteCount = 0L
  
  def writen() = position
  def remaining() = bufferSize - position
  
  def isFull() = position == bufferSize
  def hasData() = position > 0
  
  def bytesCount() = _bytesCount
  def writeCount() = _writeCount
  def directWriteCount() = _directWriteCount
  def fullWriteCount() = _fullWriteCount
  
  def put(byte: Byte) {
    if (isFull)
      flush()
    array(position) = byte
    position += 1
  }

  def put(array: Array[Byte], offset: Int, length: Int) {
    if (length > bufferSize) {
      /* 
       * No point in buffering if the length of the array is bigger than the buffer, a direct write avoids the copies 
       * and only does one 'send' system call
       */
      flush()
      os.write(array, offset, length)
      _bytesCount += length
      _directWriteCount += 1
    } else {
      var bytesPut = 0
      while (bytesPut < length) {
        if (isFull)
          flush()
        val bytesToPut = math.min(remaining, length - bytesPut)
        System.arraycopy(array, offset + bytesPut, this.array, position, bytesToPut)
        position += bytesToPut
        bytesPut += bytesToPut
      }
    }
  }

  def put(array: Array[Byte]): Unit = put(array, 0, array.length)

  /**
   * Put short value in network (big-endian) byte order.
   */
  def putShort(value: Short) = putShortBigEndian(value)

  /**
   * Put int value in network (big-endian) byte order.
   */
  def putInt(value: Int) = putIntBigEndian(value)

  private def putShortBigEndian(x: Short) {
    put(WriteBuffer.byte1(x))
    put(WriteBuffer.byte0(x))
  }

  private def putIntBigEndian(x: Int) {
    put(WriteBuffer.byte3(x))
    put(WriteBuffer.byte2(x))
    put(WriteBuffer.byte1(x))
    put(WriteBuffer.byte0(x))
  }

  def flush() {
    if (hasData) {
      os.write(array, 0, position)
      _bytesCount += position
      _writeCount += 1
      if (isFull)
        _fullWriteCount += 1
      position = 0
    }
  }

}

object WriteBuffer {
  def byte3(x: Int) = (x >> 24).toByte
  def byte2(x: Int) = (x >> 16).toByte
  def byte1(x: Int) = (x >> 8).toByte
  def byte0(x: Int) = x.toByte
}