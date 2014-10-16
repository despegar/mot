package mot

import java.nio.ByteBuffer
import scala.collection.immutable
import java.nio.charset.StandardCharsets

case class Message private[mot] (
    attributes: immutable.Seq[(String, Array[Byte])] = Nil, 
    bodyParts: immutable.Seq[ByteBuffer] = Nil) {
  
  override def toString() = {
    val attrKeys = attributes.unzip._1
    val bodySize = bodyParts.map(_.limit).sum
    s"$attributes=[${attrKeys.mkString(",")}],bodySize=$bodySize"
  }
  
  def uniquePart(): ByteBuffer = {
    if (bodyParts.length == 1)
      bodyParts.head
    else
      throw new Exception("Message has not exactly one part")   
  } 
  
  val bodyLength: Int = {
    // avoid the intermediate collection that map + sum would produce
    bodyParts.foldLeft(0)(_ + _.limit)
  }
  
  def firstAttribute(name: String): Option[Array[Byte]] = 
    attributes.find { case (n, v) => n == name } map { case (n, value) => value }
  
  def stringFirstAttribute(name: String): Option[String] = 
    firstAttribute(name).map(b => new String(b, Message.defaultEncoding))
  
  def stringBody(): String = {
    val bb = uniquePart
    new String(bb.array, bb.arrayOffset, bb.limit, Message.defaultEncoding)
  }
  
}

object Message {

  val defaultEncoding = StandardCharsets.UTF_8
  
  def fromString(attributes: Map[String, Array[Byte]], string: String): Message = 
    fromByteBuffer(attributes.to[immutable.Seq], ByteBuffer.wrap(string.getBytes(defaultEncoding)))

  def fromString(attributes: immutable.Seq[(String, Array[Byte])], string: String): Message = 
    fromByteBuffer(attributes, ByteBuffer.wrap(string.getBytes(defaultEncoding)))

  def fromArrays(attributes: Map[String, Array[Byte]], bodyParts: Array[Byte]*): Message = 
    fromByteBuffers(attributes.to[immutable.Seq], bodyParts.map(ByteBuffer.wrap): _*)

  def fromArrays(attributes: immutable.Seq[(String, Array[Byte])], bodyParts: Array[Byte]*): Message = 
    fromByteBuffers(attributes, bodyParts.map(ByteBuffer.wrap): _*)

  def fromArray(attributes: Map[String, Array[Byte]], bodyPart: Array[Byte]): Message =
    fromByteBuffer(attributes.to[immutable.Seq], ByteBuffer.wrap(bodyPart))

  def fromArray(attributes: immutable.Seq[(String, Array[Byte])], bodyPart: Array[Byte]): Message =
    fromByteBuffer(attributes, ByteBuffer.wrap(bodyPart))

  def fromByteBuffers(attributes: immutable.Seq[(String, Array[Byte])], bodyParts: ByteBuffer*): Message = {
    val immutableParts = bodyParts.to[immutable.Seq]
    validate(attributes, immutableParts)
    new Message(attributes, immutableParts)
  }

  def fromByteBuffer(attributes: immutable.Seq[(String, Array[Byte])], bodyPart: ByteBuffer): Message = {
    val parts = bodyPart :: Nil /* use :: to avoid mutable builders */
    validate(attributes, parts)
    new Message(attributes, parts)
  }

  private def validate(attributes: immutable.Seq[(String, Array[Byte])], bodyParts: immutable.Seq[ByteBuffer]) = {
    validateAttributes(attributes)
    validateBodyParts(bodyParts)
  }
  
  private def validateAttributes(attributes: immutable.Seq[(String, Array[Byte])]) = {
    for ((name, value) <- attributes) {
      if (!Util.isAscii(name))
        throw new IllegalArgumentException("Attribute names must be US-ASCII srings. Invalid name: " + name)
      if (name.length > Protocol.AttributeNameMaxLength) {
        throw new IllegalArgumentException(
            s"Attribute names cannot be longer than: ${Protocol.AttributeNameMaxLength}. Invalid name: $name")
      }
      if (value.length > Protocol.AttributeValueMaxLength) {
        throw new IllegalArgumentException(
            s"Attribute values cannot be longer than: ${Protocol.AttributeValueMaxLength}. Invalid attribute: $name")
      }
    }
  }
  
  def validateBodyParts(bodyParts: immutable.Seq[ByteBuffer]) = {
    // avoid the intermediate collection that map + sum would produce
    val totalSize = bodyParts.foldLeft(0)(_ + _.limit)
    if (totalSize > Protocol.BodyMaxLength)
      throw new IllegalArgumentException("message cannot be longer than " + Protocol.BodyMaxLength)
  }
  
}
