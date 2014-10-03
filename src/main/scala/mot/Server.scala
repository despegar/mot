package mot

import java.net.ServerSocket
import Util.FunctionToRunnable
import java.util.concurrent.TimeUnit
import scala.util.control.NonFatal
import java.util.concurrent.ConcurrentHashMap
import collection.JavaConversions._
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import Util.closeSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.net.InetAddress
import com.typesafe.scalalogging.slf4j.StrictLogging

class Server(
  val context: Context,
  val name: String,
  val bindPort: Int,
  val bindAddress: InetAddress = InetAddress.getByName("0.0.0.0"),
  val requestMaxLength: Int = 100000,
  val receivingQueueSize: Int = 5000,
  val sendingQueueSize: Int = 5000,
  val readerBufferSize: Int = 10000,
  val writerBufferSize: Int = 10000) extends StrictLogging {

  val serverSocket = new ServerSocket
  val bindSocketAddress = new InetSocketAddress(bindAddress, bindPort)
  serverSocket.bind(bindSocketAddress)
  logger.info("Server bound to " + bindSocketAddress)

  private[mot] val receivingQueue = new LinkedBlockingQueue[IncomingMessage](receivingQueueSize)

  private[mot] val connections = new ConcurrentHashMap[Address, ServerConnection]

  val acceptThread = new Thread(acceptLoop _, s"mot(${name})-acceptor")

  @volatile private var closed = false

  Protocol.checkName(name)
  context.registerServer(this)
  acceptThread.start()

  private def acceptLoop() {
    try {
      while (true) {
        val socket = serverSocket.accept()
        val conn = new ServerConnection(this, socket)
        conn.start()
      }
    } catch {
      case NonFatal(e) => context.uncaughtErrorHandler.handle(e)
    }
  }

  def receive() = {
    receivingQueue.take()
  }
  
  def receive(timeout: Long, unit: TimeUnit) = {
    receivingQueue.poll(timeout, unit)
  }

  def close() {
    closed = true
    closeSocket(serverSocket)
    acceptThread.join()
    connections.values.foreach(_.close())
    context.servers.remove(name)
  }

}
