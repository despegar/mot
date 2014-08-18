package mot

import java.net.ServerSocket
import Util.FunctionToRunnable
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.slf4j.Logging
import scala.util.control.NonFatal
import java.util.concurrent.ConcurrentHashMap
import collection.JavaConversions._
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import Util.closeSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.net.InetAddress

/**
 * The subscriber then accepts TCP connections from publishers. Flow control (i.e. reading judiciously)
 * is used to make publishers discard messages if they are too fast for this subscriber.
 *
 * Thread model:
 * - One acceptor thread
 * - One thread per incoming connection (should be normally one per publisher)
 *
 * @param bindAddress address to listen in
 * @param bindPort port to listen in
 * @param queueSize length (in messages) of the incoming message queue
 * @param bufferSize length (in bytes) of each receiving buffer (there is one buffer per connection)
 * @param uncaughtErrorHandler what to do if an uncaught exception terminates a thread
 */
class Server(
  val bindPort: Int,
  val bindAddress: InetAddress = InetAddress.getByName("0.0.0.0"),
  val requestMaxLength: Int = 100000,
  val receivingQueueSize: Int = 1000,
  val sendingQueueSize: Int = 5000,
  val readerBufferSize: Int = 10000,
  val writerBufferSize: Int = 2000,
  val uncaughtErrorHandler: UncaughtErrorHandler = LoggingErrorHandler) extends Logging {

  val serverSocket = new ServerSocket
  serverSocket.bind(new InetSocketAddress(bindAddress, bindPort))

  private[mot] val receivingQueue = new LinkedBlockingQueue[IncomingMessage](receivingQueueSize)

  private[mot] val connectors = new ConcurrentHashMap[InetSocketAddress, ServerConnection]

  val acceptThread = new Thread(acceptLoop _, s"$bindAddress-acceptor")

  @volatile private var closed = false

  acceptThread.start()

  private def acceptLoop() {
    try {
      while (true) {
        val socket = serverSocket.accept()
        val conn = new ServerConnection(this, socket)
        conn.start()
      }
    } catch {
      case NonFatal(e) => uncaughtErrorHandler.handle(e)
    }
  }

  def receive() = receivingQueue.take()
  def receive(timeout: Long, unit: TimeUnit) = receivingQueue.poll(timeout, unit)

  def close() {
    closed = true
    closeSocket(serverSocket)
    acceptThread.join()
    connectors.values.foreach(_.close())
  }

}
