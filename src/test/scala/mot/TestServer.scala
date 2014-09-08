package mot

object TestServer {

  def main(args: Array[String]) = {
	val ctx = new Context(4002)
	val server = new Server(ctx, "test-server", 5000)
	val response = Message.fromArray(Map(), "x".getBytes)
	while (true) {
	  val msg = server.receive()
	  if (msg.isRespondible)
	    msg.responder.get.sendResponse(response)
	}
  }
  
}