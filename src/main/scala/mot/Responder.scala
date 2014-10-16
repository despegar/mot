package mot

import java.util.concurrent.TimeUnit

class Responder(val connection: ServerConnectionHandler, val sequence: Int, val receptionTime: Long, val timeoutMs: Int) {

  private val timeoutNs = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
  
  /*
   * Expiration time is calculated based on time of reception, not actual sending time. This is in order to avoid any 
   * time synchronization issue between client and server, at the expense of having a small window for sending expired 
   * responses (that should be discarded by the client anyway). 
   */ 
  private[mot] val expiration = receptionTime + timeoutNs

  private var responseSent = false

  def sendResponse(response: Message): Unit = {
    if (responseSent)
      throw new ResponseAlreadySendException
    connection.sendResponse(this, response)
    responseSent = true
  }
  
  private[mot] def isOnTime(now: Long) = now <= expiration
  
  override def toString() = s"Responder(sequence=$sequence,receptionTime=$receptionTime,timeout=$timeoutMs)"
  
}
