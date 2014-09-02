package mot

class TestClient {

  def doIt(port: Int) = {
    val ctx = new Context(port)
    val client = new Client(ctx, "test-client")
    val target = Target("localhost", 5000)
    val msg = Message.fromArrays(Map(), "x".getBytes)
    var i = 0L 
    while (true) {
      client.sendMessage(target, msg)
      i += 1
      if (i % 10 == 0)
        Thread.sleep(1)
    }
  }
  
}