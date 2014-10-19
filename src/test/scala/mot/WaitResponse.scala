package mot

object WaitResponse {

  def main(args: Array[String]) = {
    val ctx = new Context(4001)
    val client = new Client(ctx, "wait-response")
    val target = Address("10.70.134.145", 5000)
    val msg = Message.fromString(Map("y" -> "value-2".getBytes), "dddd")
    var i = 0L
    while (true) {
      try {
        client.getResponse(target, msg, 1000)
        if (i % 10 == 0)
          Thread.sleep(10000)
        i += 1
      } catch {
        case e: Exception => println(e.getMessage)
      }
    }
  }

}