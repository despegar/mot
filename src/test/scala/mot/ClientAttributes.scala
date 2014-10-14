package mot

object ClientAttributes {

  def main(args: Array[String]) = {
    val ctx = new Context(4001)
    val client = new Client(ctx, "test-client-attributes")
    val target = Address("10.70.134.145", 5000)
    val msg1 = Message.fromString(Map("x" -> "value-1".getBytes), "a")
    val msg2 = Message.fromString(Map("x" -> "value-2".getBytes), "bb")
    val msg3 = Message.fromString(Map("y" -> "value-1".getBytes), "ccc")
    val msg4 = Message.fromString(Map("y" -> "value-2".getBytes), "dddd")
    while (true) {
      client.sendRequest(target, msg1, 10000, NullPromise)
      client.sendRequest(target, msg2, 10000, NullPromise)
      client.sendRequest(target, msg3, 10000, NullPromise)
      client.sendRequest(target, msg4, 10000, NullPromise)
      Thread.sleep(100)
    }
  }

}