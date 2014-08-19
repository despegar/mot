package mot

class Responder(val connection: ServerConnectionHandler, val sequence: Int, val receptionTime: Long, val timeoutMs: Int) {

  val timeoutNs = timeoutMs.toLong * 1000 * 1000
  val expiration = receptionTime + timeoutNs

  var responseSent = false

  def sendResponse(response: Message) = {
    if (responseSent)
      throw new ResponseAlreadySendException
    connection.sendResponse(this, response)
    responseSent = true
  }
  
  def isOnTime(now: Long) = now <= expiration

}
