package mot

import java.util.concurrent.TimeUnit
import mot.impl.ServerConnection
import mot.impl.OutgoingResponse
import mot.impl.ResponseFlow

class ServerConnectionHandler private[mot] (conn: ServerConnection) {

  @volatile private var _connection: Option[ServerConnection] = Some(conn)
  @volatile private var _exception: Option[Throwable] = None

  private[mot] def reportError(e: Throwable) {
    // order is important
    _exception = Some(e)
    _connection = None
  }

  private def connection() = _connection.getOrElse(throw new InvalidConnectionException(_exception.get))

  def flow(flowId: Int): Option[ResponseFlow] = connection().flow(flowId)
  
  private[mot] def offerResponse(
      serverFlowId: Int, 
      requestId: Int, 
      message: Message, 
      wait: Long, 
      timeUnit: TimeUnit): Boolean = {
    connection().offerResponse(serverFlowId, OutgoingResponse(requestId, message), wait, timeUnit)
  }
  
}