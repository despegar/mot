package mot.message

import scala.collection.immutable
import mot.BadDataException

case class ClientHello(protocolVersion: Byte, sender: String, maxLength: Int) {
  def toHelloMessage() = Hello(protocolVersion, Map(ClientHello.clientNameKey -> sender, ClientHello.maxLengthKey -> maxLength.toString))
}

object ClientHello {
  
  val maxLengthKey = "max-length"
  val clientNameKey = "client-name"
  
  def fromHelloMessage(hello: Hello) = {
    val clientName = MessageBase.getAttributeAsString(hello.attributes, clientNameKey)
    val maxLengthStr = MessageBase.getAttributeAsString(hello.attributes, maxLengthKey)
    val maxLength = try {
      maxLengthStr.toInt
    } catch {
      case e: NumberFormatException => throw new BadDataException("max-length in hello message is not a number: " + maxLengthStr)
    }
    ClientHello(hello.protocolVersion, clientName, maxLength)
  }

}
