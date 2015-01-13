package mot.impl

import java.net.Socket
import scala.util.control.NonFatal
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.LinkedBlockingQueue
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.slf4j.StrictLogging
import scala.concurrent.duration.Duration
import mot.util.Util.FunctionToRunnable
import mot.util.Util
import mot.Address
import mot.Client
import mot.Message
import mot.queue.LinkedBlockingMultiQueue
import mot.dump.Operation
import mot.dump.ConnectionEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import mot.dump.Direction
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import mot.LocalClosedException
import java.net.InetAddress

/**
 * Represents the link between the client and one server.
 * This connector will create connections and re-create them forever when they terminate with errors.
 */
class ClientConnector(val client: Client, val target: Address) extends StrictLogging {

  val creationTime = System.nanoTime()
  
  val sendingQueue = new LinkedBlockingMultiQueue[String, OutgoingEvent](client.sendingQueueSize)

  val messagesQueue = sendingQueue.addChild("messages", priority = 100)
  val flowControlQueue = sendingQueue.addChild("flowNotifications", capacity = 5, priority = 10)

  val writerThread = new Thread(connectLoop _, s"mot(${client.name})-writer-for-$target")
  
  private val closed = new AtomicBoolean

  val pendingResponses =
    new ConcurrentHashMap[Int /* request id */ , PendingResponse](
      1000 /* initial capacity */ ,
      0.5f /* load factor */ )

  val notYetConnected = new Exception("not yet connected")
  
  @volatile var currentConnection: Try[ClientConnection] = Failure(notYetConnected)

  val requestCounter = new AtomicInteger

  @volatile var lastUse = System.nanoTime()

  // It need not be atomic as the expirator has only one thread
  @volatile var timeoutsCounter = 0L

  @volatile var unrespondableSentCounter = 0L
  @volatile var respondableSentCounter = 0L
  @volatile var responsesReceivedCounter = 0L
  @volatile var triedToSendTooLargeMessage = 0L

  writerThread.start()
  logger.debug(s"Creating connector: ${client.name}->$target")

  def offerMessage(message: Message, timeout: Long, unit: TimeUnit): Boolean = {
    lastUse = System.nanoTime()
    messagesQueue.offer(OutgoingMessage(message, None), timeout, unit)
  }

  def offerRequest(message: Message, pendingResponse: PendingResponse, timeout: Long, unit: TimeUnit): Boolean = {
    lastUse = System.nanoTime()
    pendingResponse.synchronized {
      val success = messagesQueue.offer(OutgoingMessage(message, Some(pendingResponse)), timeout, unit)
      if (success) {
        pendingResponses.put(pendingResponse.requestId, pendingResponse)
        pendingResponse.scheduleExpiration()
      }
      success
    }
  }

  def connectLoop(): Unit = {
    try {
      while (!closed.get) {
        for (sock <- connectSocket()) {
          val conn = new ClientConnection(this, sock)
          currentConnection = Success(conn)
          conn.startAndBlockWriting()
          currentConnection = Failure(notYetConnected)
        }
      }
    } catch {
      case NonFatal(e) => client.context.uncaughtErrorHandler.handle(e)
    }
    logger.trace("Client connector finished")
  }

  /*
   * Connects a socket for sending messages. In case of failures, retries indefinitely
   */
  private def connectSocket(): Option[Socket] = {
    def op() = {
      logger.trace(s"Connecting to $target")
      val socket = new Socket
      // Create a socket address for each connection attempt, to avoid caching DNS resolution forever
      val resolved = InetAddress.getAllByName(target.host)(0) // DNS resolution
      val socketAddress = new InetSocketAddress(resolved, target.port)
      socket.connect(socketAddress, ClientConnector.connectTimeout)
      logger.info(s"Socket to $target connected")
      socket
    }
    val start = System.nanoTime()
    val res = Util.retryConditionally(op, closed) {
      // Must catch anything that is network-related (to retry) but nothing that could be a bug.
      // Note that DNS-related errors do not throw SocketExceptions
      case e: IOException =>
        logger.info(s"cannot connect to $target: ${e.getMessage}.")
        logger.trace("", e)
        val pc = ProspectiveConnection(target, client.name)
        client.context.dumper.dump(ConnectionEvent(pc, Direction.Outgoing, Operation.FailedAttempt, e.getMessage))
        currentConnection = Failure(e)
    }
    currentConnection = Failure(new LocalClosedException)
    res
  }

  def close(): Unit = {
    logger.debug(s"Closing connector ${client.name}->$target")
    closed.set(true)
    currentConnection.foreach(_.close())
    writerThread.join()
    currentConnection.foreach(_.readerThread.join())
  }

}

object ClientConnector {
  val connectTimeout = 3000
}
