package mot.message

import scala.collection.immutable
import mot.BadDataException

case class ServerHello(protocolVersion: Byte, name: String, maxLength: Int) {
  def toHelloMessage() = Hello(protocolVersion, Map(ServerHello.serverNameKey -> name, ServerHello.maxLengthKey -> maxLength.toString))
}

object ServerHello {

  val serverNameKey = "server-name"
  val maxLengthKey = "max-length"
  
  def fromHelloMessage(hello: Hello) = {
    val serverName = MessageBase.getAttributeAsString(hello.attributes, serverNameKey)
    val maxLengthStr = MessageBase.getAttributeAsString(hello.attributes, maxLengthKey)
    val maxLength = try {
      maxLengthStr.toInt
    } catch {
      case e: NumberFormatException => throw new BadDataException("max-length in ServerHello is not a number: " + maxLengthStr)
    }
    ServerHello(hello.protocolVersion, serverName, maxLength)
  } 
  
}