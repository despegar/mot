package mot

import java.nio.ByteBuffer
import scala.collection.immutable

/**
 * A to-be-sent message.
 *
 * @param attributes a dictionary of attributes
 * @param bodyParts a sequence of ByteBuffer instances; the parts are concatenated to form the final message
 */
case class Message private[mot] (attributes: Map[String, Array[Byte]] = Map(), bodyParts: immutable.Seq[ByteBuffer] = immutable.Seq()) {
  override def toString() = s"$attributes=[${attributes.keys.mkString(",")}],bodySize=${bodyParts.map(_.limit).sum}"
}

object Message {

  def fromArrays(attributes: Map[String, Array[Byte]], bodyParts: Array[Byte]*) = 
    fromByteBuffers(attributes, bodyParts.map(ByteBuffer.wrap): _*)

  def fromArray(attributes: Map[String, Array[Byte]], bodyPart: Array[Byte]) =
    fromByteBuffer(attributes, ByteBuffer.wrap(bodyPart))

  def fromByteBuffers(attributes: Map[String, Array[Byte]], bodyParts: ByteBuffer*) = {
    val immutableParts = bodyParts.to[immutable.Seq]
    validate(attributes, immutableParts)
    new Message(attributes, immutableParts)
  }

  def fromByteBuffer(attributes: Map[String, Array[Byte]], bodyPart: ByteBuffer) = {
    val parts = bodyPart :: Nil /* use :: to avoid mutable builders */
    validate(attributes, parts)
    new Message(attributes, parts)
  }

  def validate(attributes: Map[String, Array[Byte]], bodyParts: immutable.Seq[ByteBuffer]) = {
    validateAttributes(attributes)
    validateBodyParts(bodyParts)
  }
  
  def validateAttributes(attributes: Map[String, Array[Byte]]) {
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
