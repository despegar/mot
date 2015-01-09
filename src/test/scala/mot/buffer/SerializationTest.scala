package mot.buffer

import org.scalatest.FunSuite
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream

class SerializationTest extends FunSuite {

  val val8 = ((1 << 8) - 1).toShort
  val val16 = (1 << 16) - 1
  val val24 = (1 << 24) - 1
  val val31 = (1 << 31) - 1
  val val32 = (1.toLong << 32) - 1
  
  test("buffer serialization") {
    val baos = new ByteArrayOutputStream(100)
    val wb = new WriteBuffer(baos, 10)
    wb.put8(val8)
    wb.put16(val16)
    wb.put24(val24)
    wb.put31(val31)
    wb.flush()
    val bais = new ByteArrayInputStream(baos.toByteArray)
    val rb = new ReadBuffer(bais, 10)
    assert(rb.get8() == val8)
    assert(rb.get16() == val16)
    assert(rb.get24() == val24)
    assert(rb.get31() == val31)
  }
  
}