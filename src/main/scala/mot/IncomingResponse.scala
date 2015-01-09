package mot

import scala.util.Try

case class IncomingResponse(result: Try[Message], clientFlow: ClientFlow)