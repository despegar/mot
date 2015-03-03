package mot

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

import com.typesafe.scalalogging.slf4j.StrictLogging

import io.netty.util.HashedWheelTimer
import mot.impl.ClientConnector
import mot.impl.PendingResponse
import mot.impl.Protocol
import mot.util.NamedThreadFactory
import mot.util.Promise
import mot.util.UnaryPromise
import mot.util.Util.FunctionToRunnable

/**
 * Mot client. Instantiate this class to create a client-side Mot protocol engine.
 *
 * @constructor Create a new Mot client.
 * 
 * @param context 
 *   Context in which this client will be registered.
 * @param name 
 *   Name of this client. Must be unique in the context and will be reported to the servers.
 * @param maxLength 
 *   Maximum allowable response length, in bytes, this length is informed to the server. Connections that send messages 
 *   longer than the maximum are dropped to a simple DOS attack with larger-than-allowed messages.
 * @param maxQueueSize 
 *   Maximum length (in messages) of the sending queue size. Too big a value uses too much memory, too little can 
 *   degrade latency as the queue is empty too much time. There is one queue per counterpart.
 * @param readBufferSize 
 *   Size (in bytes) of the reader buffer
 * @param writeBufferSize 
 *   Size (in bytes) of the writer buffer
 * @param connectorGc 
 *   Time after which an idle connection will be closed.
 * @param tolerance 
 *   Time after which a client stop accepting messages if the connection cannot be established. Mot can accept (and 
 *   enqueue) messages even if the underlying connection failed or cannot be created. Setting the tolerance to a lower 
 *   value improves error detection, at the expense of not taking advantage of intermittent connections. Setting it to 
 *   a higher value (or infinite) marked the interface more resilient, at the expense of not reporting connection 
 *   errors until the requests time out.  
 *  
 * @define target 
 *   Message destination.
 * @define message 
 *   Message to send.
 * @define timeoutMs 
 *   How much to wait for the response, in milliseconds.
 * @define promise 
 *   A promise that will receive the response when it arrives (or will receive the error). 
 *   [[mot.util.UnaryPromise]] provides a convenient one-value promise for simple cases.  
 * @define flow 
 *   A flow to associate with this message, this can be latter used to control the arrival of responses.
 * @define enqueingTimeout 
 *   How much time to wait for the enqueuing of the message. The queue will be fill if the server is not 
 *   <em>reading</em> requests fast enough. This has nothing to to with the time the server takes to respond, but it
 *   would be possible that an server with too many pending responses choose to stop reading new requests. A 
 *   non-blocking client would use a zero timeout here, in combination with a large enough queue (in order to be
 *   able to keep pace with transitory differences in velocities).
 * @define enqueingTimeoutUnit 
 *   Unit of the enqueingTimeout
 */
class Client(
  val context: Context,
  val name: String,
  val maxLength: Int = 100000,
  val maxQueueSize: Int = 1000,
  val readBufferSize: Int = 10000,
  val writeBufferSize: Int = 5000,
  val connectorGc: FiniteDuration = Duration(600, TimeUnit.SECONDS),
  val tolerance: Duration = Duration.Inf)
  extends MotParty with StrictLogging {

  private val idCounter = new AtomicInteger

  private[mot] val connectors = new ConcurrentHashMap[Address, ClientConnector]

  @volatile private var closed = false

  /**
   * Main flow, always created and used as default when no flow is specified. Opening and closing the mainFlow is 
   * possible but not recommended.
   */
  val mainFlow = createFlow() // main flow is always created

  Protocol.checkName(name)
  context.registerClient(this)

  /**
   * Create a client flow. Objects created using this method can be associated to outgoing messages and after that used
   * for response flow control.
   */
  def createFlow() = new ClientFlow(idCounter.getAndIncrement(), this)

  private val connectorExpirator = {
    val ex = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory(s"mot($name)-connector-expirator"))
    val runDelay = Duration(1, TimeUnit.SECONDS)
    ex.scheduleWithFixedDelay(connectorExpiratorTask _, runDelay.length, runDelay.length, runDelay.unit)
    ex
  }

  private def connectorExpiratorTask(): Unit = try {
    val threshold = System.nanoTime() - connectorGc.toNanos
    val it = connectors.entrySet.iterator
    while (it.hasNext) {
      val entry = it.next
      val (target, connector) = (entry.getKey, entry.getValue)
      if (connector.lastUse < threshold) {
        logger.debug(s"Expiring connector to ${connector.target} after $connectorGc of inactivity")
        it.remove()
        connector.close()
      }
    }
  } catch {
    case NonFatal(e) => context.uncaughtErrorHandler.handle(e)
  }

  private[mot] val promiseExpirator = new HashedWheelTimer(
    new NamedThreadFactory(s"mot($name)-promise-expiratior"),
    200 /* tick duration */ , TimeUnit.MILLISECONDS,
    1000 /* ticks per wheel */ )

  private def getConnector(target: Address) = {
    var connector = connectors.get(target)
    if (connector == null) {
      connector = synchronized {
        val value = connectors.get(target)
        if (value == null) {
          val newValue = new ClientConnector(this, target)
          connectors.put(target, newValue)
          newValue
        } else {
          value
        }
      }
    }
    connector.currentConnection match {
      case Success(conn) =>
      // pass
      case Failure(ex) =>
        val now = System.nanoTime()
        val delay = Duration(now - connector.creationTime, TimeUnit.NANOSECONDS)
        if (delay > tolerance)
          throw new ErrorStateException(ex)
    }
    connector
  }

  /**
   * Send a request and block until the response arrives or the timeout expires.
   */
  def getResponse(target: Address, message: Message, timeoutMs: Int, flow: ClientFlow = mainFlow): Message = {
    val promise = new UnaryPromise[IncomingResponse]
    offerRequest(target, message, timeoutMs, promise, flow, Long.MaxValue, TimeUnit.DAYS) // long enough
    val incomingResponse = promise.result() 
    // block forever at the promise level, because Mot's timeout will make it fail eventually
    incomingResponse.message.get
  }

  /**
   * Offer a request. When the response arrives, complete the promise passed as an argument. This method overload
   * allows to specify a flow to use and whether (and how much time) to block waiting for space in the sending queue.
   * 
   * @param target $target 
   * @param message $message
   * @param timeoutMs $timeoutMs
   * @param promise $promise
   * @param flow $flow
   * @param enqueingTimeout $enqueingTimeout
   * @param enqueingTimeoutUnit $enqueingTimeoutUnit
   * 
   * @return Whether the message could be enqueued or the corresponding queue overflowed.
   */
  def offerRequest(
    target: Address,
    message: Message,
    timeoutMs: Int,
    promise: Promise[IncomingResponse],
    flow: ClientFlow,
    enqueingTimeout: Long,
    enqueingTimeoutUnit: TimeUnit): Boolean = {
    checkClosed()
    if (!flow.isOpen)
      throw new IllegalStateException("Cannot send messages associated with a closed flow")
    checkTimeout(timeoutMs)
    val connector = getConnector(target)
    val pendingResponse = new PendingResponse(promise, timeoutMs, connector, flow)
    connector.offerRequest(message, pendingResponse, enqueingTimeout, enqueingTimeoutUnit)
  }

  /**
   * Offer a request. When the response arrives, complete the promise passed as an argument. This method overload
   * allows to specify a flow to use.
   * 
   * @param target $target 
   * @param message $message 
   * @param timeoutMs $timeoutMs
   * @param promise $promise
   * @param flow $flow
   * 
   * @return Whether the message could be enqueued or the corresponding queue overflowed.
   */
  def offerRequest(
    target: Address, message: Message, timeoutMs: Int, promise: Promise[IncomingResponse], flow: ClientFlow): Boolean =
    offerRequest(target, message, timeoutMs, promise, flow, 0, TimeUnit.NANOSECONDS)

  /**
   * Offer a request. When the response arrives, complete the promise passed as an argument. This method overload
   * allows to specify whether (and how much time) to block waiting for space in the sending queue.
   * 
   * @param target $target 
   * @param message $message
   * @param timeoutMs $timeoutMs
   * @param promise $promise
   * @param enqueingTimeout $enqueingTimeout
   * @param enqueingTimeoutUnit $enqueingTimeoutUnit
   */
  def offerRequest(
    target: Address, 
    message: Message, 
    timeoutMs: Int, 
    promise: Promise[IncomingResponse], 
    enqueingTimeout: Long, 
    enqueingTimeoutUnit: TimeUnit): Boolean =
    offerRequest(target, message, timeoutMs, promise, mainFlow, enqueingTimeout, enqueingTimeoutUnit)

  /**
   * Offer a request. When the response arrives, complete the promise passed as an argument.
   * 
   * @param target $target 
   * @param message $message
   * @param timeoutMs $timeoutMs
   * @param promise $promise
   */
  def offerRequest(
    target: Address, message: Message, timeoutMs: Int, promise: Promise[IncomingResponse]): Boolean =
    offerRequest(target, message, timeoutMs, promise, mainFlow, 0, TimeUnit.NANOSECONDS)

  /**
   * Offer an unrespondible message. This method overload allows to specify whether (and how much time) to block 
   * waiting for space in the sending queue.
   * 
   * @param target $target 
   * @param message $message
   * @param enqueingTimeout $enqueingTimeout
   * @param enqueingTimeoutUnit $enqueingTimeoutUnit
   * @return true if the message could be enqueued, false otherwise
   */
  def offerMessage(target: Address, message: Message, enqueingTimeout: Long, enqueingTimeoutUnit: TimeUnit): Boolean = {
    checkClosed()
    val connector = getConnector(target)
    connector.offerMessage(message, enqueingTimeout, enqueingTimeoutUnit)
  }

  /**
   * Offer an unrespondible message.
   * 
   * @param target $target 
   * @param message $message
   */
  def offerMessage(target: Address, message: Message): Boolean =
    offerMessage(target, message, 0, TimeUnit.NANOSECONDS)

  private def checkTimeout(timeoutMs: Int) = {
    if (timeoutMs > connectorGc.toMillis)
      throw new IllegalArgumentException(s"Request timeout cannot be longer than client GC time ($connectorGc)")
  }

  private def checkClosed() = {
    if (closed)
      throw new IllegalStateException("Client closed")
  }

  /**
   * Close the client. Calling this method terminates all threads and connections.
   */
  def close(): Unit = {
    logger.debug("Closing client " + name)
    closed = true
    context.clients.remove(name)
    connectors.values.foreach(_.close())
    promiseExpirator.stop()
    connectorExpirator.shutdown()
  }

  override def toString() = s"Client(name=$name)"

}	
