package mot

import java.util.concurrent.TimeUnit

/**
 * Instances of this class are created by server-side parties as handlers for responding messages. Each respondible
 * request has one distinct instance.
 */
class Responder private[mot](
    val connectionHandler: ServerConnectionHandler, 
    val requestId: Int, 
    val timeoutMs: Int,
    val serverFlowId: Int) {
    
  private var responseSent = false

  /**
   * Offer a response. If the sending queue is full, wait the specified time. Return whether the response could be enqueued. 
   */
  def offer(message: Message, wait: Long, timeUnit: TimeUnit): Boolean = synchronized {
    if (responseSent)
      throw new ResponseAlreadySendException
    val success = connectionHandler.offerResponse(serverFlowId, requestId, message, wait, timeUnit)
    if (success)
      responseSent = true
    success
  }
  
  /**
   * Offer a response. Return whether the response could be enqueued. Never block.
   */
  def offer(message: Message): Boolean = offer(message, 0, TimeUnit.NANOSECONDS)
  
  override def toString() = 
    s"Responder(connection=$connectionHandler,requestId=$requestId,timeout=${timeoutMs}ms,serverFlowId=$serverFlowId)"
  
}
