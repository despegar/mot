package mot

import scala.util.Try

/**
 * Represent an incoming response to a Mot client. It can also represent a failure sending a message.
 */
case class IncomingResponse private[mot] (
    remoteAddress: Address, 
    localAddress: Option[Address], 
    message: Try[Message], 
    clientFlow: ClientFlow)
    