package mot.impl

import mot.InvalidServerConnectionException

class ServerConnectionHandler(conn: ServerConnection) {
  
  @volatile private var _connection: Option[ServerConnection] = Some(conn)
  @volatile private var _exception: Option[Throwable] = None
  
  def reportError(e: Throwable) {
    // order is important
    _exception = Some(e)
    _connection = None
  }
  
  def isValid() = _connection.isDefined
  def connection() = _connection.getOrElse(throw new InvalidServerConnectionException(_exception.get))
  
}