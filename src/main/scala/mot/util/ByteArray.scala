package mot.util

import java.nio.charset.Charset

class ByteArray private (val array: Array[Byte], val offset: Int, val length: Int) {

  def apply(i: Int) = array(offset + i)

  override def equals(ob: Any): Boolean = ob match {
    case that: ByteArray =>
      if (this.length != that.length)
        return false
      var i = this.length - 1
      var j = that.length - 1
      while (i >= 0) {
        if (this(i) != that(j))
          return false
        i -= 1
        j -= 1
      }
      true
    case _ =>
      false
  }

  override def hashCode() = {
    var h = 1
    var i = length - 1
    while (i >= 0) {
      h = 31 * h + apply(i).toInt
      i -= 1
    }
    h
  }

  def asString(encoding: Charset) = new String(array, offset, length, encoding)

}

object ByteArray {

  def apply(array: Array[Byte], offset: Int, length: Int) = new ByteArray(array, offset, length)
  def apply(array: Array[Byte]) = new ByteArray(array, 0, array.length)
  def allocate(length: Int) = apply(Array.ofDim(length))
  def fromString(string: String, encoding: Charset) = apply(string.getBytes(encoding))

}