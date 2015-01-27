package mot

import java.nio.charset.StandardCharsets
import mot.util.Util
import mot.impl.Protocol
import mot.util.ByteArray

case class Message private[mot] (
    attributes: Seq[(String, ByteArray)] = Nil, 
    bodyLength: Int,
    bodyParts: Seq[ByteArray] = Nil) {
  
  override def toString() = {
    val attrKeys = attributes.unzip._1
    val bodySize = bodyParts.map(_.length).sum
    s"$attributes=[${attrKeys.mkString(",")}],bodySize=$bodySize"
  }
  
  def uniquePart(): ByteArray = {
    if (bodyParts.length == 1)
      bodyParts.head
    else
      throw new Exception("Message has not exactly one part")   
  } 
  
  def firstAttribute(name: String): Option[ByteArray] = 
    attributes.find { case (n, v) => n == name } map { case (n, value) => value }
  
  def stringFirstAttribute(name: String): Option[String] = 
    firstAttribute(name).map(b => b.asString(Message.defaultEncoding))
  
  def stringBody() = uniquePart.asString(Message.defaultEncoding)
  
}

object Message {

  val defaultEncoding = StandardCharsets.UTF_8
  
  def fromString(attributes: Map[String, ByteArray], string: String): Message = 
    fromByteArray(attributes, ByteArray.fromString(string, defaultEncoding))

  def fromString(attributes: Seq[(String, ByteArray)], string: String): Message = 
    fromByteArray(attributes, ByteArray.fromString(string, defaultEncoding))

  def fromString(string: String): Message = fromByteArray(Nil, ByteArray.fromString(string, defaultEncoding))

  def fromByteArrays(attributes: Seq[(String, ByteArray)], bodyParts: ByteArray*): Message = {
    validate(attributes, bodyParts)
    val bodyLength = bodyParts.foldLeft(0)(_ + _.length)
    new Message(attributes, bodyLength, bodyParts)
  }
  
  def fromByteArrays(attributes: Map[String, ByteArray], bodyParts: ByteArray*): Message =
    fromByteArrays(attributes, bodyParts: _*)

  def fromByteArrays(bodyParts: ByteArray*): Message = fromByteArrays(Nil, bodyParts: _*)

  def fromByteArray(attributes: Seq[(String, ByteArray)], bodyPart: ByteArray): Message = {
    val parts = bodyPart :: Nil /* use :: to avoid mutable builders */
    validate(attributes, parts)
    val bodyLength = parts.foldLeft(0)(_ + _.length)
    new Message(attributes, bodyLength, parts)
  }
  
  def fromByteArray(attributes: Map[String, ByteArray], bodyPart: ByteArray): Message = 
    fromByteArray(attributes.toSeq, bodyPart)

  def fromByteArray(bodyPart: ByteArray): Message = fromByteArray(Nil, bodyPart)

  private def validate(attributes: Seq[(String, ByteArray)], bodyParts: Seq[ByteArray]) = {
    validateAttributes(attributes)
    validateBodyParts(bodyParts)
  }
  
  private def validateAttributes(attributes: Seq[(String, ByteArray)]) = {
    import Limits._
    for ((name, value) <- attributes) {
      if (!Util.isAscii(name))
        throw new IllegalArgumentException("Attribute names must be US-ASCII srings. Invalid name: " + name)
      if (name.length > AttributeNameMaxLength) {
        throw new IllegalArgumentException(
            s"Attribute names cannot be longer than: $AttributeNameMaxLength. Invalid name: $name")
      }
      if (value.length > AttributeValueMaxLength) {
        throw new IllegalArgumentException(
            s"Attribute values cannot be longer than: $AttributeValueMaxLength. Invalid attribute: $name")
      }
    }
  }
  
  def validateBodyParts(bodyParts: Seq[ByteArray]) = {
    import Limits._
    // avoid the intermediate collection that map + sum would produce
    val totalSize = bodyParts.foldLeft(0)(_ + _.length)
    if (totalSize > BodyMaxLength)
      throw new IllegalArgumentException("message cannot be longer than " + BodyMaxLength)
  }
  
}
