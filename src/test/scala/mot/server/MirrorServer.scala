package mot.server

import mot.util.Util.FunctionToRunnable
import com.typesafe.scalalogging.slf4j.StrictLogging
import mot.Context
import mot.Message
import mot.Server
import java.util.concurrent.TimeUnit
import scala.io.StdIn
import java.util.concurrent.atomic.AtomicLong
import mot.IncomingMessage
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import scala.util.control.NonFatal
import java.util.concurrent.LinkedBlockingQueue

object MirrorServer extends StrictLogging {

  val responseOverflow = new AtomicLong

  def main(args: Array[String]): Unit = {
    val ctx = new Context(monitoringPort = args(0).toInt, dumpPort = args(1).toInt)
    val executor = new ThreadPoolExecutor(
        4, 4, 0L, TimeUnit.SECONDS, new SynchronousQueue[Runnable], new ThreadPoolExecutor.CallerRunsPolicy)
    val server = new Server(
      ctx,
      "test-server",
      executor,
      handleRequest,
      bindPort = 5000,
      maxLength = 1000000000,
      maxQueueSize = 200000,
      readBufferSize = 200000,
      writeBufferSize = 200000)
    Console.println("Press return to exit")
    StdIn.readLine()
    ctx.close()
    executor.shutdown()
    executor.awaitTermination(Long.MaxValue, TimeUnit.NANOSECONDS)
  }

  def handleRequest(incoming: IncomingMessage): Unit = try {
    for (responder <- incoming.responderOption) {
      val success = responder.offer(Message.fromByteArrays(incoming.message.bodyParts: _*))
      if (!success)
        responseOverflow.incrementAndGet()
    }
  } catch {
    case NonFatal(e) => logger.info("Cannot send response", e)
  }

}