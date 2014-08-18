package mot

import java.nio.ByteBuffer

object MessageValidator {

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
  
  def validateBodyParts(bodyParts: Seq[ByteBuffer]) = {
    if (bodyParts.map(_.limit).sum > Protocol.BodyMaxLength)
      throw new IllegalArgumentException("message cannot be longer than " + Protocol.BodyMaxLength)
  }
  
}