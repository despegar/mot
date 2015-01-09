package mot.impl

import mot.protocol.HelloFrame
import HelloKeys._

object HelloKeys {
  val versionKey = "version"
  val clientNameKey = "client-name"
  val serverNameKey = "server-name"
  val maxLengthKey = "max-length"
}

trait HelloMessage {
  def toHelloMessage(): HelloFrame
}

case class ClientHello(protocolVersion: Int, sender: String, maxLength: Int) extends HelloMessage {
  def toHelloMessage() = HelloFrame.fromMap(Map(
    versionKey -> Protocol.ProtocolVersion.toString, 
    clientNameKey -> sender, 
    maxLengthKey -> maxLength.toString))
}

object ClientHello {
  def fromHelloMessage(hello: HelloFrame) = {
    val map = hello.attributesAsStringMap
    val protocolVersion = HelloFrame.getIntAttribute(map, versionKey)
    val clientName = HelloFrame.getAttribute(map, clientNameKey)
    val maxLength = HelloFrame.getIntAttribute(map, maxLengthKey)
    ClientHello(protocolVersion, clientName, maxLength)
  }
}

case class ServerHello(protocolVersion: Int, name: String, maxLength: Int) extends HelloMessage {
  def toHelloMessage() = HelloFrame.fromMap(Map(
    versionKey -> Protocol.ProtocolVersion.toString, 
    serverNameKey -> name, 
    maxLengthKey -> maxLength.toString))
}

object ServerHello {
  def fromHelloMessage(hello: HelloFrame) = {
    val map = hello.attributesAsStringMap
    val protocolVersion = HelloFrame.getIntAttribute(map, versionKey)
    val serverName = HelloFrame.getAttribute(map, serverNameKey)
    val maxLength = HelloFrame.getIntAttribute(map, maxLengthKey)
    ServerHello(protocolVersion, serverName, maxLength)
  } 
}
