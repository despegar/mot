package mot.client

import mot.util.Util.FunctionToRunnable
import mot.Context
import mot.Client
import mot.Address
import mot.Message
import mot.NullPromise
import java.util.concurrent.TimeUnit
import scala.io.StdIn
import mot.util.Promise
import mot.DelayPromise
import mot.util.ByteArray

object TestThroughput extends TestClient {

  def main(args: Array[String]): Unit = {
    val (resp, monitoringPort, dumpPort, target, proxyOpt) = parseArgs(args)
    val ctx = new Context(monitoringPort, dumpPort)
    val client = new Client(
        ctx, 
        "test-client", 
        sendingQueueSize = 200000, 
        readerBufferSize = 200000, 
        writerBufferSize = 200000)
    val body = "x"
    val (realTarget, message) = proxyOpt match {
      case Some(proxy) => (proxy, Message.fromString(Map("Proxy" -> ByteArray(target.toString.getBytes)), body))
      case None => (target, Message.fromString(body))
    }
    @volatile var closed = false
    def send() = {
      while (!closed) {
        var offered = false
        while (!offered) {
          offered = if (resp)
            client.offerRequest(realTarget, message, 10000, DelayPromise, 200, TimeUnit.MILLISECONDS)
          else
            client.offerMessage(target, message, 200, TimeUnit.MILLISECONDS)
        }
      }
    }
    val clientThread1 = new Thread(send _, "client-thread-1")
    val clientThread2 = new Thread(send _, "client-thread-2")
    clientThread1.start()
    clientThread2.start()
    Console.println("Press return to exit")
    var c = -1
    while (c != 'q'.toByte) {
      c match {
        case 'a' => DelayPromise.delay = 0
        case 'b' => DelayPromise.delay = 100
        case _ => // pass
      }
      c = System.in.read()
    }
    ctx.close()
    closed = true
    clientThread1.join()
    clientThread2.join()
  }

}