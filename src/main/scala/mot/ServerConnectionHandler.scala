package mot

class ServerConnectionHandler(conn: ServerConnection) {
  
  @volatile private var connection: Option[ServerConnection] = Some(conn)
  @volatile private var _exception: Option[Throwable] = None
  
  def reportError(e: Throwable) {
    // Order is important
    _exception = Some(e)
    connection = None
  }
  
  def exception() = _exception.get
  
  def isValid() = connection.isDefined
  
  def sendResponse(requestRef: Responder, response: Message) = {
    val conn = connection.getOrElse(throw new InvalidServerConnectionException(_exception.get))
    conn.sendResponse(requestRef, response)
  }
  
}