package mot.message

import scala.collection.immutable
import mot.Protocol

case class ServerHello(protocolVersion: Int, name: String, maxLength: Int) {
  import Hello._
  def toHelloMessage() =
    Hello(Map(versionKey -> Protocol.ProtocolVersion.toString, serverNameKey -> name, maxLengthKey -> maxLength.toString))
}

object ServerHello {

  def fromHelloMessage(hello: Hello) = {
    val protocolVersion = MessageBase.getIntAttribute(hello.attributes, Hello.versionKey)
    val serverName = MessageBase.getAttribute(hello.attributes, Hello.serverNameKey)
    val maxLength = MessageBase.getIntAttribute(hello.attributes, Hello.maxLengthKey)
    ServerHello(protocolVersion, serverName, maxLength)
  } 
  
}