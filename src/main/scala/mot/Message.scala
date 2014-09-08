package mot

import java.nio.ByteBuffer
import scala.collection.immutable

/**
 * A to-be-sent message.
 *
 * @param attributes a dictionary of attributes
 * @param bodyParts a sequence of ByteBuffer instances; the parts are concatenated to form the final message
 */
case class Message private (attributes: Map[String, Array[Byte]] = Map(), bodyParts: immutable.Seq[ByteBuffer] = immutable.Seq()) {

  MessageValidator.validateAttributes(attributes)
  MessageValidator.validateBodyParts(bodyParts)
  
  override def toString() = s"$attributes=[${attributes.keys.mkString(",")}],bodySize=${bodyParts.map(_.limit).sum}"

}

object Message {

  def fromArrays(attributes: Map[String, Array[Byte]], bodyParts: Array[Byte]*) =
    fromByteBuffers(attributes, bodyParts.map(ByteBuffer.wrap): _*)

  def fromByteBuffers(attributes: Map[String, Array[Byte]], bodyParts: ByteBuffer*) = 
    new Message(attributes, bodyParts.to[immutable.Seq])

  def fromArray(attributes: Map[String, Array[Byte]], bodyPart: Array[Byte]) =
    fromByteBuffer(attributes, ByteBuffer.wrap(bodyPart))

  def fromByteBuffer(attributes: Map[String, Array[Byte]], bodyPart: ByteBuffer) = 
    new Message(attributes, immutable.Seq(bodyPart))
  
}
