package mot

import java.nio.charset.StandardCharsets
import mot.util.Util
import mot.impl.Protocol
import mot.util.ByteArray

/**
 * A Mot message. This class represents Mot messages, either outgoing or incoming. In order to construct an instance,
 * one of the factory methods of the companion object must be used.
 *
 * @param attributes
 *   Attributes of the message.
 * @param bodyLength
 *   Length of the body, in bytes.
 * @param bodyParts
 *   Return the parts that form the body of the message. The body is represented internally as a 
 *   [[scala.collection.Seq]] of [[mot.util.ByteArray]] instances. In the wire, the arrays are concatenated and the 
 *   receiving side cannot see the original segmentation. The segmentation of the body is useful for constructing 
 *   message from various parts without having the copy to one contiguous array. The incoming messages always have one 
 *   part. 
 */
final class Message private[mot] (
  val attributes: Seq[(String, ByteArray)] = Nil,
  val bodyLength: Int,
  val bodyParts: Seq[ByteArray] = Nil) {

  override def toString() = {
    val attrKeys = attributes.unzip._1
    val bodySize = bodyParts.map(_.length).sum
    s"$attributes=[${attrKeys.mkString(",")}],bodySize=$bodySize"
  }

  /**
   * Return the body as one byte array. If the message was received, this method returns the only part, without any 
   * copying; otherwise, the parts are copied into a single array.
   */
  def body(): ByteArray = {
    bodyParts match {
      case Seq(head) =>
        head
      case parts =>
        val res = ByteArray.allocate(bodyLength)
        var pos = 0
        for (part <- parts) {
          System.arraycopy(part.array, 0, res.array, pos, part.length)
          pos += part.length
        }
        res
    }
  }

  /**
   * Return the body (originally a byte array) converted to a string using the [[mot.Message.defaultEncoding]].  If the 
   * message was received, this method returns the only part, without any copying; otherwise, the parts are copied into 
   * a single array.
   */
  def stringBody() = body().asString(Message.defaultEncoding)

  /**
   * Return the first attribute that has the name passed as an argument.
   */
  def firstAttribute(name: String): Option[ByteArray] =
    attributes.find { case (n, v) => n == name } map { case (n, value) => value }

  /**
   * Return the first attribute that has the name passed as an argument and then convert it to a string using
   * [[mot.Message.defaultEncoding]].
   */
  def stringFirstAttribute(name: String): Option[String] =
    firstAttribute(name).map(b => b.asString(Message.defaultEncoding))

}

object Message {

  /**
   * Character encoding used for the factory methods that convert strings to byte arrays or vice versa.
   * It is currently UTF-8.
   */
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

  private def validateBodyParts(bodyParts: Seq[ByteArray]) = {
    import Limits._
    // avoid the intermediate collection that map + sum would produce
    val totalSize = bodyParts.foldLeft(0)(_ + _.length)
    if (totalSize > BodyMaxLength)
      throw new IllegalArgumentException("message cannot be longer than " + BodyMaxLength)
  }

}
