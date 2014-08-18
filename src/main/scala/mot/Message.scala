package mot

import java.nio.ByteBuffer

/**
 * A to-be-sent message.
 *
 * @param attributes a dictionary of attributes
 * @param bodyParts a sequence of ByteBuffer instances; the parts are concatenated to form the final message
 */
case class Message private (attributes: Map[String, Array[Byte]] = Map(), bodyParts: Seq[ByteBuffer] = Seq()) {

  MessageValidator.validateAttributes(attributes)
  MessageValidator.validateBodyParts(bodyParts)
  
  val bodySize = bodyParts.map(_.limit).sum
  
  override def toString() = s"$attributes=[${attributes.keys.mkString(",")}],bodySize=$bodySize"

}

object Message {

  def fromArrays(attributes: Map[String, Array[Byte]], bodyParts: Array[Byte]*) =
    fromByteBuffers(attributes, bodyParts.map(ByteBuffer.wrap): _*)

  def fromByteBuffers(attributes: Map[String, Array[Byte]], bodyParts: ByteBuffer*) = 
    new Message(attributes, bodyParts)

}
