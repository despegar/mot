package mot

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.util.control.NonFatal
import com.typesafe.scalalogging.slf4j.StrictLogging
import mot.util.Util.FunctionToRunnable
import mot.util.Util.closeSocket
import mot.impl.Protocol
import mot.impl.ServerConnection
import mot.util.Util
import java.util.concurrent.ScheduledThreadPoolExecutor
import mot.util.NamedThreadFactory
import scala.concurrent.duration.Duration
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * Mot Server.
 *
 * @param context the context in which this server will be registered
 * @param name the name of this server, must be unique in the context and will be reported to the clients
 * @param executor Executor that will be used to process incoming messages
 * @param handler function that will be submitted to the executor for every incoming message
 * @param bindPort TCP port to bind
 * @param bindAddress IP address to bind
 * @param maxAcceptedLength maximum allowable request length, in bytes, this length is informed to the client.
 *     Connections that send messages longer than the maximum are dropped to a simple DOS attack with
 *     larger-than-allowed messages.
 * @param sendingQueueSize maximum length (in messages) of the sending queue size. Too big a value uses too much memory,
 *     too little can degrade latency as the queue is empty too much time. There is one queue per counterpart.
 * @param readerBufferSize size (in bytes) of the reader buffer
 * @param writerBufferSize size (in bytes) of the writer buffer
 */
class Server(
  val context: Context,
  val name: String,
  val executor: Executor,
  val handler: IncomingMessage => Unit,
  val bindPort: Int,
  val bindAddress: InetAddress = InetAddress.getByName("0.0.0.0"),
  val maxAcceptedLength: Int = 100000,
  val sendingQueueSize: Int = 1000,
  val readerBufferSize: Int = 10000,
  val writerBufferSize: Int = 5000)
  extends MotParty with StrictLogging {

  val sendingQueueSaturationThreshold = 0.85
  val sendingQueueRecoveryThreshold = 0.4

  private val serverSocket = new ServerSocket
  private val bindSocketAddress = new InetSocketAddress(bindAddress, bindPort)
  serverSocket.bind(bindSocketAddress)
  logger.info("Server bound to " + bindSocketAddress)

  private[mot] val connections = new ConcurrentHashMap[Address, ServerConnection]

  private val acceptThread = new Thread(acceptLoop _, s"mot(${name})-acceptor")

  @volatile private var closed = false

  Protocol.checkName(name)
  context.registerServer(this)
  acceptThread.start()

  private def acceptLoop(): Unit = {
    try {
      while (true) {
        val socket = serverSocket.accept()
        val conn = new ServerConnection(this, socket)
        conn.start()
      }
    } catch {
      case NonFatal(e) if closed => // pass
      case NonFatal(e) if !closed => context.uncaughtErrorHandler.handle(e)
    }
  }

  /**
   * Close the server. Calling this method terminates all threads and connections.
   */
  def close(): Unit = {
    closed = true
    closeSocket(serverSocket)
    flowExpirator.shutdown()
    acceptThread.join()
    connections.values.foreach(_.close())
    connections.values.foreach(_.writerThread.join())
    connections.values.foreach(_.readerThread.join())
    context.servers.remove(name)
  }

  private val flowExpirator = {
    val ex = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory(s"mot-server($name)-flow-expirator"))
    val runDelay = Duration(60, TimeUnit.SECONDS)
    ex.scheduleWithFixedDelay(flowExpiratorTask _, runDelay.length, runDelay.length, runDelay.unit)
    ex
  }

  private def flowExpiratorTask(): Unit = {
    try {
      connections.values.foreach(_.responseFlows.removeOldFlows())
    } catch {
      case NonFatal(e) => context.uncaughtErrorHandler.handle(e)
    }
  }

}
