package mot

import scala.util.Try

case class IncomingResponse private[mot] (
    remoteAddress: Address, 
    localAddress: Option[Address], 
    message: Try[Message], 
    clientFlow: ClientFlow)