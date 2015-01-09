package mot.protocol

import scala.collection.immutable
import mot.util.Util
import mot.buffer.ReadBuffer
import java.nio.charset.StandardCharsets.US_ASCII
import mot.buffer.WriteBuffer
import java.io.OutputStream
import mot.util.ByteArray

trait AttributesSupport {

  import AttributesSupport._
  
  def attributes: immutable.Seq[(String, ByteArray)]

  def attributesLength() = {
    var len = 1 // attribute quantity
    // use old-style loop to avoid for-comprehension overhead
    var i = 0
    while (i < attributes.size) {
      val (name, value) = attributes(i)
      len += 1 // name length
      len += name.length // will encode in ASCII
      len += Util.bytes16 // value length
      len += value.length
      i += 1
    }
    len
  }

  def writeAttributes(writeBuffer: WriteBuffer): Unit = {
    writeBuffer.put(attributes.size.toByte)
    // use old-style loop to avoid for-comprehension overhead
    var i = 0
    while (i < attributes.size) {
      val (name, value) = attributes(i)
      writeByteSizeAsciiString(writeBuffer, asciiString = name)
      writeShortByteField(writeBuffer, value)
      i += 1
    }
  }

  def dumpAttributes(os: OutputStream) = {
    for ((name, value) <- attributes) {
      // attribute values are byte arrays, we do not know their encoding, so we send them as they are, hoping that
      // the terminal encoding matches the attributes'
      os.write(s"$name: ".getBytes(US_ASCII))
      os.write(value.array)
      os.write('\n')
    }
  }

}

object AttributesSupport {

  def readAttributes(readBuffer: ReadBuffer): immutable.Seq[(String, ByteArray)] = {
    val size = readBuffer.get
    if (size < 0)
      throw ProtocolSyntaxException("negative attribute number: " + size)
    // optimize no-attributes case
    if (size == 0)
      return Vector.empty
    for (i <- 0 until size) yield {
      val nameLength = readBuffer.get()
      val name = readBuffer.getByteArray(nameLength).asString(US_ASCII)
      val valueLength = readBuffer.get16()
      val value = readBuffer.getByteArray(valueLength)
      name -> value
    }
  }
  
  def writeByteSizeAsciiString(writeBuffer: WriteBuffer, asciiString: String) = {
    val targetSize = asciiString.length // ASCII guarantees one byte per character
    if (targetSize > Byte.MaxValue)
      throw new IllegalArgumentException("Array length cannot be longer than " + Byte.MaxValue)
    writeBuffer.put(targetSize.toByte)
    writeBuffer.put(ByteArray.fromString(asciiString, US_ASCII))
  }

  def writeShortByteField(writeBuffer: WriteBuffer, byteArray: ByteArray) = {
    if (byteArray.length > Short.MaxValue)
      throw new IllegalArgumentException("Array length cannot be longer than " + Short.MaxValue)
    writeBuffer.put16(byteArray.length)
    writeBuffer.put(byteArray)
  }

}