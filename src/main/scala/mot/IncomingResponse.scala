package mot

import scala.util.Try

case class IncomingResponse(
    remoteAddress: Address, 
    localAddress: Option[Address], 
    result: Try[Message], 
    clientFlow: ClientFlow)