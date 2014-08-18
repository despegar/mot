package mot

class Responder(val connection: ServerConnectionHandler, val sequence: Int, val receptionTime: Long, val timeout: Int) {

  var responseSent = false
  
  def sendResponse(response: Message) = {
    if (responseSent) 
      throw new ResponseAlreadySendException
    connection.sendResponse(this, response)
    responseSent = true
  }
  
}
