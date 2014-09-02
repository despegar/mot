package mot

class TestServer {

  def doIt(port: Int) = {
	val ctx = new Context(port)
	val server = new Server(ctx, "test-server", 5000)
	while (true) {
	  val msg = server.receive()
	}
  }
  
}