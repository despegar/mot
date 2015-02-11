package mot

import scala.util.Try

case class IncomingResponse(
    remoteAddress: Option[Address], 
    localAddress: Option[Address], 
    result: Try[Message], 
    clientFlow: ClientFlow)