package mot

import scala.collection.immutable
import java.nio.charset.StandardCharsets
import mot.util.Util
import mot.impl.Protocol
import mot.util.ByteArray

case class Message private[mot] (
    attributes: immutable.Seq[(String, ByteArray)] = Nil, 
    bodyLength: Int,
    bodyParts: immutable.Seq[ByteArray] = Nil) {
  
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
    fromByteArray(attributes.to[immutable.Seq], ByteArray.fromString(string, defaultEncoding))

  def fromString(attributes: immutable.Seq[(String, ByteArray)], string: String): Message = 
    fromByteArray(attributes, ByteArray.fromString(string, defaultEncoding))

  def fromByteArrays(attributes: immutable.Seq[(String, ByteArray)], bodyParts: ByteArray*): Message = {
    val immutableParts = bodyParts.to[immutable.Seq]
    validate(attributes, immutableParts)
    val bodyLength = immutableParts.foldLeft(0)(_ + _.length)
    new Message(attributes, bodyLength, immutableParts)
  }
  
  def fromByteArrays(attributes: Map[String, ByteArray], bodyParts: ByteArray*): Message =
    fromByteArrays(attributes.to[immutable.Seq], bodyParts: _*)

  def fromByteArray(attributes: immutable.Seq[(String, ByteArray)], bodyPart: ByteArray): Message = {
    val parts = bodyPart :: Nil /* use :: to avoid mutable builders */
    validate(attributes, parts)
    val bodyLength = parts.foldLeft(0)(_ + _.length)
    new Message(attributes, bodyLength, parts)
  }
  
  def fromByteArray(attributes: immutable.Map[String, ByteArray], bodyPart: ByteArray): Message = 
    fromByteArray(attributes.to[immutable.Seq], bodyPart)

  private def validate(attributes: immutable.Seq[(String, ByteArray)], bodyParts: immutable.Seq[ByteArray]) = {
    validateAttributes(attributes)
    validateBodyParts(bodyParts)
  }
  
  private def validateAttributes(attributes: immutable.Seq[(String, ByteArray)]) = {
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
  
  def validateBodyParts(bodyParts: immutable.Seq[ByteArray]) = {
    import Limits._
    // avoid the intermediate collection that map + sum would produce
    val totalSize = bodyParts.foldLeft(0)(_ + _.length)
    if (totalSize > BodyMaxLength)
      throw new IllegalArgumentException("message cannot be longer than " + BodyMaxLength)
  }
  
}
