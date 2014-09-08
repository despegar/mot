package mot

object TestRespClient {

  def main(args: Array[String]) = {
    val ctx = new Context(4001)
    val client = new Client(ctx, "test-client", queueSize = 5000, readerBufferSize = 100000, writerBufferSize = 100000)
    val target = Address("localhost", 5000)
    val msg = Message.fromArray(Nil, "x".getBytes)
    var i = 0L 
    while (true) {
      val res = client.sendRequest(target, msg, 60000)
    }
  }
  
}