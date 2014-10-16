package mot

case class IncomingMessage(responderOption: Option[Responder], from: Address, client: String, maxResponseLength: Int, message: Message) {
  def isRespondable(): Boolean = responderOption.isDefined
  def responder(): Responder = responderOption.getOrElse(throw new MessageNotRespondableException)
}
