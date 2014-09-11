package mot

import java.net.Socket
import Util.FunctionToRunnable
import com.typesafe.scalalogging.slf4j.Logging
import scala.util.control.NonFatal
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.LinkedBlockingQueue
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Promise
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/**
 * Represents the link between the client and one server.
 * This connector will create connections and re-create them forever when they terminate with errors.
 */
class ClientConnector(val client: Client, val target: Address) extends Logging {

  val sendingQueue = new LinkedBlockingQueue[(Message, Option[PendingResponse])](client.queueSize)

  val thread = new Thread(connectLoop _, s"mot-client-connector-${client.name}->$target")
  val closed = new AtomicBoolean

  @volatile var currentConnection: Option[ClientConnection] = None

  /**
   * Hold the last exception than occurred trying to establish a connection. Does not hold exceptions
   * produced during the connection.
   */
  @volatile var lastConnectingException: Option[Throwable] = None

  @volatile var lastUse = System.nanoTime()

  // It need not be atomic as the expirator has only one thread
  @volatile var timeoutsCounter = 0L

  val promiseExpirator = {
    val tf = new ThreadFactory {
      def newThread(r: Runnable) =
        new Thread(r, s"mot-client-promise-expiratior-${client.name}->$target")
    }
    val stpe = new ScheduledThreadPoolExecutor(1, tf)
    // Reduce memory footprint, as the happy path (the response arriving) implies task cancellation 
    stpe.setRemoveOnCancelPolicy(true)
    stpe
  }

  thread.start()
  logger.debug(s"Creating connector: ${client.name}->$target")

  def isConnected() = currentConnection.isDefined
  def lastConnectingError() = lastConnectingException
  def isErrorState() = lastConnectingException.isDefined

  def offerMessage(message: Message) = {
    lastUse = System.nanoTime()
    sendingQueue.offer((message, None))
  }

  def putMessage(message: Message) = {
    lastUse = System.nanoTime()
    sendingQueue.put((message, None))
  }

  def offerRequest(message: Message, pendingResponse: PendingResponse) = {
    lastUse = System.nanoTime()
    /* 
     * It is necessary to schedule the expiration before enqueuing to avoid a race condition between the assignment of the variable
     * with the task and the arrival of the response (in case it arrives quickly). This forces to unschedule the task if the 
     * enqueueing fails.
     */  
    pendingResponse.scheduleExpiration()
    val success = sendingQueue.offer((message, Some(pendingResponse)))
    if (!success)
      pendingResponse.unscheduleExpiration()
    success
  }

  def putRequest(message: Message, pendingResponse: PendingResponse) = {
    lastUse = System.nanoTime()
    pendingResponse.scheduleExpiration()
    sendingQueue.put((message, Some(pendingResponse)))
  }

  def connectLoop() = {
    try {
      var socket = connectSocket()
      while (!closed.get) {
        val conn = new ClientConnection(this, socket.get)
        currentConnection = Some(conn)
        conn.startAndBlockWriting()
        currentConnection = None
        socket = connectSocket()
      }
    } catch {
      case NonFatal(e) => client.context.uncaughtErrorHandler.handle(e)
    }
    logger.trace("Client connector finished")
  }

  /*
   * Connects a socket for sending messages. In case of failures, retries indefinitely
   */
  private def connectSocket() = {
    def op() = {
      logger.trace(s"Connecting to $target")
      val socket = new Socket
      // Create a socket address for each connection attempt, to avoid caching DNS resolution forever
      val socketAddress = new InetSocketAddress(target.host, target.port)
      socket.connect(socketAddress, client.connectTimeout)
      logger.debug(s"Socket to $target connected")
      socket
    }
    val start = System.nanoTime()
    val res = Util.retryConditionally(op, closed) {
      // Must catch anything that is network-related (to retry) but nothing that could be a bug.
      // Note that DNS-related errors do not throw SocketExceptions
      case e: IOException =>
        val delay = System.nanoTime() - start
        if (delay > 10 * 1000 * 1000 * 1000)
          lastConnectingException = Some(e) // used also as a flag of error (when isDefined)
        logger.error(s"Error connecting to $target. Retrying.", e)
    }
    lastConnectingException = None
    res
  }

  def close() {
    logger.debug(s"Closing connector ${client.name}->$target")
    closed.set(true)
    currentConnection.foreach(_.close())
    thread.join()
    currentConnection.foreach(_.readerThread.join())
  }

}
