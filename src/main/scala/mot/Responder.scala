package mot

import mot.impl.ServerConnectionHandler
import mot.impl.OutgoingResponse

class Responder private[mot](
    val connectionHandler: ServerConnectionHandler, 
    val requestId: Int, 
    val timeoutMs: Int,
    val serverFlowId: Int) {
    
  private var responseSent = false

  // TODO: Prevent the Connection object to escape
  def connection() = connectionHandler.connection
  
  /**
   * Offer a response. Return whether the response could be enqueued. There is no way to wait until the response
   * is in the queue. The suggested action in case of rejection is to log the error o do something of the sort (i.e. 
   * increment some error counter). 
   * Note that there is never a good idea to block here, because back-pressure cannot be applied in the general case
   * of a party serving more than one client. Response overflows arise when a client is sending requests faster than
   * it can read their responses, or has bandwidth to receive them. A cautious client can avoid this situation 
   * limiting the number of pending requests it has at any time, which also is a good idea regardless of this 
   * situation.
   */
  def offerResponse(message: Message): Boolean = synchronized {
    if (responseSent)
      throw new ResponseAlreadySendException
    val success = connectionHandler.connection.offerResponse(serverFlowId, OutgoingResponse(requestId, message))
    if (success)
      responseSent = true
    success
  }
  
  override def toString() = 
    s"Responder(connection=$connectionHandler,requestId=$requestId,timeout=${timeoutMs}ms,serverFlowId=$serverFlowId)"
  
}
