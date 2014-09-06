package mot

import java.net.InetSocketAddress

case class IncomingMessage(
    responder: Option[Responder], fromAddress: InetSocketAddress, client: String, message: Message) {
  
  def isRespondible = responder.isDefined
  
}
