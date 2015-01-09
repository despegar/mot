package mot.client

import mot.Client
import mot.Context
import mot.Message
import mot.NullPromise
import mot.Address
import mot.util.ByteArray

object ClientAttributes {

  def main(args: Array[String]) = {
    val ctx = new Context(4001)
    val client = new Client(ctx, "test-client-attributes")
    val target = Address("10.70.134.145", 5000)
    val msg1 = Message.fromString(Map("x" -> ByteArray("value-1".getBytes)), "a")
    val msg2 = Message.fromString(Map("x" -> ByteArray("value-2".getBytes)), "bb")
    val msg3 = Message.fromString(Map("y" -> ByteArray("value-1".getBytes)), "ccc")
    val msg4 = Message.fromString(Map("y" -> ByteArray("value-2".getBytes)), "dddd")
    while (true) {
      client.offerRequest(target, msg1, 10000, NullPromise)
      client.offerRequest(target, msg2, 10000, NullPromise)
      client.offerRequest(target, msg3, 10000, NullPromise)
      client.offerRequest(target, msg4, 10000, NullPromise)
      Thread.sleep(100)
    }
  }

}