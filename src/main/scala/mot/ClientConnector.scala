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
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents the link between the client and one server.
 * This connector will create connections and re-create them forever when they terminate with errors.
 */
class ClientConnector(val client: Client, val target: Target) extends Logging {

  val sendingQueue = new LinkedBlockingQueue[(Message, Option[ResponsePromise])](client.queueSize)

  val thread = new Thread(writeLoop _, s"mor-client-connector-${client.name}->$target")
  val closed = new AtomicBoolean

  val unrespondableSentCounter = new AtomicLong 
  val respondableSentCounter = new AtomicLong 
  val responsesReceivedCounter = new AtomicLong 
  val timeoutsCounter = new AtomicLong 
  
  @volatile var currentConnection: Option[ClientConnection] = None
  
  /**
   * Hold the last exception than occurred trying to establish a connection. Does not hold exceptions
   * produced during the connection.
   */
  @volatile var lastConnectingException: Option[Throwable] = None

  thread.start()

  def isConnected() = currentConnection.isDefined
  def lastConnectingError() = lastConnectingException
  def isErrorState() = lastConnectingException.isDefined

  def offer(message: Message, responsePlaceholder: Option[ResponsePromise]) =
    sendingQueue.offer((message, responsePlaceholder))

  def put(message: Message, responsePlaceholder: Option[ResponsePromise]) =
    sendingQueue.put((message, responsePlaceholder))

  def writeLoop() = {
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
    closed.set(true)
    currentConnection.foreach(_.close())
    thread.join()
    currentConnection.foreach(_.readerThread.join())
  }

}
