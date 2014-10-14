package mot.message

import mot.Protocol
import java.nio.charset.StandardCharsets
import scala.collection.immutable

case class ClientHello(protocolVersion: Int, sender: String, maxLength: Int) {
  import Hello._
  def toHelloMessage() = {
    Hello.fromMap(Map(
        versionKey -> Protocol.ProtocolVersion.toString, 
        clientNameKey -> sender, 
        maxLengthKey -> maxLength.toString))
  }
}

object ClientHello {
  
  def fromHelloMessage(hello: Hello) = {
    val map = hello.attributesAsStringMap
    val protocolVersion = Hello.getIntAttribute(map, Hello.versionKey)
    val clientName = Hello.getAttribute(map, Hello.clientNameKey)
    val maxLength = Hello.getIntAttribute(map, Hello.maxLengthKey)
    ClientHello(protocolVersion, clientName, maxLength)
  }

}
