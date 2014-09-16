package mot

import Util.FunctionToRunnable
import com.typesafe.scalalogging.slf4j.StrictLogging

object SlowServer extends StrictLogging {

  def main(args: Array[String]) = {
    val ctx = new Context(4002)
    val server = new Server(ctx, "test-server", 5000, 
        receivingQueueSize = 100000, sendingQueueSize = 100000, readerBufferSize = 100000, writerBufferSize = 100000)
    val response = Message.fromArray(Nil, "x".getBytes)
    def receive() = {
      while (true) {
        val msg = server.receive()
      }
    }
    val serverThread1 = new Thread(receive _, "server-thread-1").start()
    val serverThread2 = new Thread(receive _, "server-thread-2").start()
    val serverThread3 = new Thread(receive _, "server-thread-3").start()
    val serverThread4 = new Thread(receive _, "server-thread-4").start()
  }

}