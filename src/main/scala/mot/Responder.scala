package mot

class Responder(val connection: ServerConnectionHandler, val sequence: Int, val receptionTime: Long, val timeoutMs: Int) {

  val timeoutNs = timeoutMs.toLong * 1000 * 1000
  
  /*
   * Expiration time is calculated based on time of reception, not actual sending time. This is in order to avoid any time
   * synchronization issue between client and server, at the expense of having a small window for sending expired responses (that
   * should be discarded by the client anyway). 
   */ 
  val expiration = receptionTime + timeoutNs

  var responseSent = false

  def sendResponse(response: Message) = {
    if (responseSent)
      throw new ResponseAlreadySendException
    connection.sendResponse(this, response)
    responseSent = true
  }
  
  def isOnTime(now: Long) = now <= expiration
  
  override def toString() = s"Responder(sequence=$sequence,receptionTime=$receptionTime,timeout=$timeoutMs)"
  
}
