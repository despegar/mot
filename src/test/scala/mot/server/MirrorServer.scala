package mot.server

import mot.util.Util.FunctionToRunnable
import com.typesafe.scalalogging.slf4j.StrictLogging
import mot.Context
import mot.Message
import mot.Server
import java.util.concurrent.TimeUnit
import scala.io.StdIn
import java.util.concurrent.atomic.AtomicLong

object MirrorServer extends StrictLogging {

  val responseOverflow = new AtomicLong
  
  def main(args: Array[String]) = {
    val ctx = new Context(monitoringPort = args(0).toInt, dumpPort = args(1).toInt)
    val server = new Server(
      ctx,
      "test-server",
      bindPort = 5000,
      maxAcceptedLength = 1000000000,
      receivingQueueSize = 200000,
      sendingQueueSize = 200000,
      readerBufferSize = 200000,
      writerBufferSize = 200000)
    @volatile var closed = false
    def receive() = {
      while (!closed) {
        val incoming = server.poll(200, TimeUnit.MILLISECONDS)
        if (incoming != null) {
          try {
            for (responder <- incoming.responderOption) {
              val success = responder.offer(Message.fromByteArrays(Nil, incoming.message.bodyParts: _*))
              if (!success)
                responseOverflow.incrementAndGet()
            }
          } catch {
            case e: Exception => logger.info("Cannot send response", e)
          }
        }
      }
    }
    val serverThread1 = new Thread(receive _, "server-thread-1")
    val serverThread2 = new Thread(receive _, "server-thread-2")
    serverThread1.start()
    serverThread2.start()
    Console.println("Press return to exit")
    StdIn.readLine()
    ctx.close()
    closed = true
    serverThread1.join()
    serverThread2.join()
  }

}