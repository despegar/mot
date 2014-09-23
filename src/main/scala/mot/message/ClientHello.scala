package mot.message

import mot.Protocol

case class ClientHello(protocolVersion: Int, sender: String, maxLength: Int) {
  import Hello._
  def toHelloMessage() = 
    Hello(Map(versionKey -> Protocol.ProtocolVersion.toString, clientNameKey -> sender, maxLengthKey -> maxLength.toString))
}

object ClientHello {
  
  def fromHelloMessage(hello: Hello) = {
    val protocolVersion = MessageBase.getIntAttribute(hello.attributes, Hello.versionKey)
    val clientName = MessageBase.getAttribute(hello.attributes, Hello.clientNameKey)
    val maxLength = MessageBase.getIntAttribute(hello.attributes, Hello.maxLengthKey)
    ClientHello(protocolVersion, clientName, maxLength)
  }

}
