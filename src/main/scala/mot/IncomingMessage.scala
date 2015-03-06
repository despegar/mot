package mot

/**
 * Represent an incoming message to a Mot server.
 */
case class IncomingMessage private[mot] (
    responderOption: Option[Responder], 
    remoteAddress: Address, 
    localAddress: Address,
    client: String, 
    maxResponseLength: Int, 
    message: Message) {
  def isRespondable(): Boolean = responderOption.isDefined
  def responder(): Responder = responderOption.getOrElse(throw new NotRespondableException)
}
