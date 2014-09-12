package mot

case class IncomingMessage(responder: Option[Responder], fromAddress: Address, client: String, maxResponseLength: Int, message: Message) {
  def isRespondible = responder.isDefined
}
