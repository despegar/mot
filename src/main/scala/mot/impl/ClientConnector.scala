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
import mot.dump.TcpEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import mot.dump.Direction
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import mot.LocalClosedException
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.net.SocketTimeoutException

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
  logger.debug(s"creating connector: ${client.name}->$target")

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
        connectSocket()
        for (conn <- currentConnection)
          conn.startAndBlockWriting()
      }
    } catch {
      case NonFatal(e) => client.context.uncaughtErrorHandler.handle(e)
    }
    logger.trace("Client connector finished")
  }

  val minimumDelay = Duration(1000, TimeUnit.MILLISECONDS)

  /*
   * Connects a socket for sending messages. In case of failures, retries indefinitely
   */
  private def connectSocket(): Unit = {
    val dumper = client.context.dumper
    val prospConn = ProspectiveConnection(target, client.name)
    currentConnection = Failure(notYetConnected)
    var lastAttempt = -1L
    while (!closed.get && currentConnection.isFailure) {
      waitIfNecessary(lastAttempt)
      lastAttempt = System.nanoTime()
      try {
        val resolved = InetAddress.getAllByName(target.host).toSeq // DNS resolution
        var i = 0
        while (!closed.get && currentConnection.isFailure && i < resolved.size) {
          val addr = resolved(i).getHostAddress
          val addrStr = s"${target.host}/$addr:${target.port}"
          val socketAddress = new InetSocketAddress(addr, target.port)
          try {
            val socket = new Socket
            logger.debug(s"connecting to $addrStr (address ${i + 1} of ${resolved.size})")
            socket.connect(socketAddress, ClientConnector.connectTimeout)
            logger.info(s"socket to $addrStr connected")
            currentConnection = Success(new ClientConnection(this, socket))
          } catch {
            case e @ (_: SocketException | _: SocketTimeoutException) =>
              logger.info(s"cannot connect to $addrStr: ${e.getMessage}.")
              logger.trace("", e)
              dumper.dump(TcpEvent(prospConn, Direction.Outgoing, Operation.FailedAttempt, e.getMessage))
              currentConnection = Failure(e)
          }
          i += 1
        }
      } catch {
        case e: UnknownHostException =>
          logger.info(s"cannot resolve ${target.host}: ${e.getMessage}.")
          logger.trace("", e)
          dumper.dump(TcpEvent(prospConn, Direction.Outgoing, Operation.FailedNameResolution, e.getMessage))
          currentConnection = Failure(e)
      }
    }
    if (closed.get)
      currentConnection = Failure(new LocalClosedException)
  }

  private def waitIfNecessary(lastAttempt: Long): Unit = {
    val elapsed = Duration(System.nanoTime() - lastAttempt, TimeUnit.NANOSECONDS)
    val remaining = minimumDelay - elapsed
    Thread.sleep(math.max(remaining.toMillis, 0))
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
