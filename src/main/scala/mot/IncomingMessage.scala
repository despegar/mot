package mot

case class IncomingMessage(responder: Option[Responder], fromAddress: Address, client: String, message: Message) {
  def isRespondible = responder.isDefined
}
