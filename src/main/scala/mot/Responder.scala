package mot

import java.util.concurrent.TimeUnit
import mot.impl.ServerConnection
import mot.impl.OutgoingResponse

/**
 * Instances of this class are created by server-side parties as handlers for responding messages. Each respondible
 * request has one distinct instance.
 */
final class Responder private[mot](
    private val connection: ServerConnection, 
    val requestId: Int, 
    val timeoutMs: Int,
    val flow: ServerFlow) {
    
  private var responseSent = false

  /**
   * Offer a response. If the sending queue is full, wait the specified time. Return whether the response could be enqueued. 
   */
  def offer(message: Message, wait: Long, timeUnit: TimeUnit): Boolean = synchronized {
    if (responseSent)
      throw new ResponseAlreadySendException
    val success = connection.offerResponse(flow, OutgoingResponse(requestId, message), wait, timeUnit)
    if (success)
      responseSent = true
    success
  }

  /**
   * Offer a response. Return whether the response could be enqueued. Never block.
   */
  def offer(message: Message): Boolean = offer(message, 0, TimeUnit.NANOSECONDS)
  
  override def toString() = 
    s"Responder(connection=$connection,requestId=$requestId,timeout=${timeoutMs}ms,serverFlowId=$flow)"
  
}
