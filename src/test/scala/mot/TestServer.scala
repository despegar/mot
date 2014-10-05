package mot

import Util.FunctionToRunnable
import com.typesafe.scalalogging.slf4j.StrictLogging

object TestServer extends StrictLogging {

  def main(args: Array[String]) = {
    val ctx = new Context(4002)
    val server = new Server(ctx, "test-server", 5000, requestMaxLength = 1000000000,
        receivingQueueSize = 100000, sendingQueueSize = 100000, readerBufferSize = 100000, writerBufferSize = 100000)
    val response = Message.fromArray(Nil, "x".getBytes)
    def receive() = {
      while (true) {
        val msg = server.receive()
        try {
          msg.responderOption.foreach(r => if (r.isOnTime(System.nanoTime())) r.sendResponse(response))
        } catch {
          case e: TooLateException => // nothing
          case e: Exception => logger.info("Cannot send response", e)
        }
      }
    }
    val serverThread1 = new Thread(receive _, "server-thread-1").start()
    val serverThread2 = new Thread(receive _, "server-thread-2").start()
    val serverThread3 = new Thread(receive _, "server-thread-3").start()
    val serverThread4 = new Thread(receive _, "server-thread-4").start()
  }

}