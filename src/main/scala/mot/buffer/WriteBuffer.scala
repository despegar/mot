package mot.buffer

import java.io.OutputStream
import mot.util.ByteArray
import java.util.concurrent.atomic.AtomicLong
import mot.util.Util.RichAtomicLong

class WriteBuffer(val os: OutputStream, val bufferSize: Int) {

  import WriteBuffer._
  
  val array = Array.ofDim[Byte](bufferSize)
  
  private var position = 0
  
  private val _bytesCount = new AtomicLong
  private val _writeCount = new AtomicLong
  private val _directWriteCount = new AtomicLong
  private val _fullWriteCount = new AtomicLong
  
  def writen() = position
  def remaining() = bufferSize - position
  
  def isFull() = position == bufferSize
  def hasData() = position > 0
  
  def bytesCount() = _bytesCount.get
  def writeCount() = _writeCount.get
  def directWriteCount() = _directWriteCount.get
  def fullWriteCount() = _fullWriteCount
  
  private var _lastFlush = System.nanoTime()
  
  def lastFlush() = _lastFlush
  
  def put(byte: Byte) {
    if (isFull)
      flush()
    array(position) = byte
    position += 1
  }
  
  def put(toWrite: ByteArray) {
    if (array.length > bufferSize) {
      /* 
       * No point in buffering if the length of the array is bigger than the buffer, a direct write avoids the copies 
       * and only does one 'send' system call
       */
      flush()
      os.write(toWrite.array, toWrite.offset, toWrite.length)
      _bytesCount.lazyAdd(toWrite.length)
      _directWriteCount.lazyIncrement()
    } else {
      var bytesPut = 0
      while (bytesPut < toWrite.length) {
        if (isFull())
          flush()
        val bytesToPut = math.min(remaining, toWrite.length - bytesPut)
        System.arraycopy(toWrite.array, toWrite.offset + bytesPut, this.array, position, bytesToPut)
        position += bytesToPut
        bytesPut += bytesToPut
      }
    }
  }

  def flush() {
    if (hasData) {
      os.write(array, 0, position)
      _bytesCount.lazyAdd(position)
      _writeCount.lazyIncrement()
      if (isFull)
        _fullWriteCount.lazyIncrement()
      position = 0
    }
    _lastFlush = System.nanoTime()
  }

  // All methods below write big-endian (network) unsigned integers
  
  def put8(x: Short) {
    assert(x >= 0)
    assert(x < Max8)
    put(byte0(x))
  }
  
  def put16(x: Int) {
    assert(x >= 0)
    assert(x < Max16)
    put(byte1(x))
    put(byte0(x))
  }

  def put24(x: Int) {
    assert(x >= 0)
    assert(x < Max24)
    put(byte2(x))
    put(byte1(x))
    put(byte0(x))
  }
  
  def put31(x: Int) {
    assert(x >= 0)
    assert(x < Max31)
    put(byte3(x))
    put(byte2(x))
    put(byte1(x))
    put(byte0(x))
  }
  
}

object WriteBuffer {
  
  val Max8 = 1 << 8
  val Max16 = 1 << 16
  val Max24 = 1 << 24
  val Max31 = 1L << 31
  val Max32 = 1L << 32
  
  def byte3(x: Long) = (x >> 24).toByte
  def byte2(x: Long) = (x >> 16).toByte
  def byte1(x: Long) = (x >> 8).toByte
  def byte0(x: Long) = x.toByte
  
  def byte3(x: Int) = (x >> 24).toByte
  def byte2(x: Int) = (x >> 16).toByte
  def byte1(x: Int) = (x >> 8).toByte
  def byte0(x: Int) = x.toByte
  
}