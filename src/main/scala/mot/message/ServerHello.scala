package mot.message

import mot.Protocol

case class ServerHello(protocolVersion: Int, name: String, maxLength: Int) {
  import Hello._
  def toHelloMessage() = {
    Hello.fromMap(Map(
        versionKey -> Protocol.ProtocolVersion.toString, 
        serverNameKey -> name, 
        maxLengthKey -> maxLength.toString))
  }
}

object ServerHello {

  def fromHelloMessage(hello: Hello) = {
    val map = hello.attributesAsStringMap
    val protocolVersion = Hello.getIntAttribute(map, Hello.versionKey)
    val serverName = Hello.getAttribute(map, Hello.serverNameKey)
    val maxLength = Hello.getIntAttribute(map, Hello.maxLengthKey)
    ServerHello(protocolVersion, serverName, maxLength)
  } 
  
}