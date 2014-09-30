package mot

case class IncomingMessage(responderOption: Option[Responder], from: Address, client: String, maxResponseLength: Int, message: Message) {
  def isRespondable() = responderOption.isDefined
  def responder() = responderOption.getOrElse(throw new MessageNotRespondableException)
}
