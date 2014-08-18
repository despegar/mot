package mot.message

import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer

case class Response(
  requestReference: Int,
  attributes: Map[String, Array[Byte]],
  bodyParts: Seq[Array[Byte]]) extends MessageBase {

  val bodySize = bodyParts.map(_.length).sum

  def writeToBuffer(writeBuffer: WriteBuffer) = {
    writeBuffer.put(MessageType.Response.id.toByte)
    writeBuffer.putInt(requestReference)
    MessageBase.writeAttributes(writeBuffer, attributes)
    MessageBase.writeIntSizeByteMultiField(writeBuffer, bodyParts)
  }

  override def toString() =
    s"Response(reqRef=$requestReference,attributes=[${attributes.keys.mkString(",")}],bodySize=$bodySize)"

}

object Response {

  def factory(readBuffer: ReadBuffer, maxLength: Int) = {
    val requestReference = readBuffer.getInt()
    val attributes = MessageBase.readAttributes(readBuffer)
    val body = MessageBase.readIntSizeByteField(readBuffer, maxLength)
    // TODO: Ver qué hacer con los atributos repetidos
    Response(requestReference, attributes.toMap, Seq(body))
  }

}