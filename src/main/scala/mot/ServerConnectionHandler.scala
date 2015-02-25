package mot

import java.util.concurrent.TimeUnit
import mot.impl.ServerConnection
import mot.impl.OutgoingResponse

class ServerConnectionHandler private[mot] (conn: ServerConnection) {

  @volatile private var _connection: Option[ServerConnection] = Some(conn)
  @volatile private var _exception: Option[Throwable] = None

  private[mot] def reportError(e: Throwable) {
    // order is important
    _exception = Some(e)
    _connection = None
  }

  def isValid() = _connection.isDefined
  
  private def connection() = _connection.getOrElse(throw new InvalidConnectionException(_exception.get))

  def offerResponse(serverFlowId: Int, requestId: Int, message: Message, wait: Long, timeUnit: TimeUnit): Boolean =
    connection().offerResponse(serverFlowId, OutgoingResponse(requestId, message), wait, timeUnit)
  
  def isSaturated(flowId: Int) = connection().flow(flowId).isSaturated()
  def isRecovered(flowId: Int) = connection().flow(flowId).isRecovered()
    
  def remoteName() = connection().remoteAddress
  def remoteAddress() = connection().remoteAddress
  
}