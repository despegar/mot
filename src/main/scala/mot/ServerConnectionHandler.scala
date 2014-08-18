package mot

class ServerConnectionHandler(conn: ServerConnection) {
  
  @volatile private var connection: Option[ServerConnection] = Some(conn)
  @volatile private var exception: Option[Throwable] = None
  
  def reportError(e: Throwable) {
    // Order is important
    exception = Some(e)
    connection = None
  }
  
  def sendResponse(requestRef: Responder, response: Message) = {
    val conn = connection.getOrElse(throw new InvalidServerConnectionException(exception.get))
    conn.sendResponse(requestRef, response)
  }
  
}