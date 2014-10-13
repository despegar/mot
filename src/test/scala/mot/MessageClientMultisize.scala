package mot

import java.util.concurrent.TimeUnit
import Util.FunctionToRunnable
import mot.util.Promise
import scala.util.Try

object MessageClientMultisize {

  def main(args: Array[String]) = {
    val ctx = new Context(4001)
    val client = new Client(
        ctx, "test-client", sendingQueueSize = 100000, readerBufferSize = 10000, writerBufferSize = 5000)
    val target = Address("10.70.134.145", 5000)
    val sizes = (0 to 5).map(math.pow(10, _).toInt).view.force
    val msgs = sizes.map(size => (size, Message.fromArray(Nil, Array.fill(size * 1000)('x'.toByte)))).toMap
    var i = 1L
    val rsizes = sizes.reverse
    while (true) {
      val msg = msgs(rsizes.find(i % _ == 0).get)
      val res = client.sendRequest(target, msg, 10000, NullPromise)
      i += 1
    }
  }

}