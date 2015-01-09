package mot

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.util.control.NonFatal
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.util.HashedWheelTimer
import mot.util.Util.FunctionToRunnable
import mot.impl.Protocol
import mot.impl.ClientConnector
import mot.impl.PendingResponse
import java.util.concurrent.BlockingQueue
import scala.util.Try
import mot.util.Promise
import mot.util.UnaryPromise
import java.util.concurrent.ScheduledThreadPoolExecutor
import mot.util.NamedThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration
import scala.util.Success
import scala.util.Failure

/**
 * Mot Client.
 *
 * @param context the context in which this client will be registered
 * @param name the name of this client, must be unique in the context and will be reported to the servers
 * @param responseMaxLength maximum allowable response length, in bytes, this length is informed to the server.
 *     Connections that send messages longer than the maximum are dropped to a simple DOS attack with
 *     larger-than-allowed messages.
 * @param sendingQueueSize maximum length (in messages) of the sending queue size. Too big a value uses too much memory,
 *     too little can degrade latency as the queue is empty too much time. There is one queue per counterpart.
 * @param readerBufferSize size (in bytes) of the reader buffer
 * @param writerBufferSize size (in bytes) of the writer buffer
 * @param connectorGcSec time (in seconds) after which an idle connection will be closed.
 * @param pessimistic whether or not enqueue new messages if the connection cannot be established. Setting this to true
 *     improve error detection, at the expense of not taking advantage of intermittent connections.
 */
class Client(
  val context: Context,
  val name: String,
  val maxAcceptedLength: Int = 100000,
  val sendingQueueSize: Int = 1000,
  val readerBufferSize: Int = 10000,
  val writerBufferSize: Int = 5000,
  val connectorGc: FiniteDuration = Duration(600, TimeUnit.SECONDS),
  val tolerance: Duration = Duration.Inf) extends MotParty with StrictLogging {

  private val idCounter = new AtomicInteger

  def createFlow() = new ClientFlow(idCounter.getAndIncrement(), this)

  val mainFlow = createFlow() // main flow is always created
  private[mot] val connectors = new ConcurrentHashMap[Address, ClientConnector]

  @volatile private var closed = false

  private val connectorExpirator = {
    val ex = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory(s"mot($name)-connector-expirator"))
    val runDelay = Duration(1, TimeUnit.SECONDS)
    ex.scheduleWithFixedDelay(connectorExpiratorTask _, runDelay.length, runDelay.length, runDelay.unit)
    ex
  }

  private def connectorExpiratorTask() = {
    try {
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
  }

  Protocol.checkName(name)
  context.registerClient(this)

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
    // Block forever at the promise level, because Mot's timeout will make it fail eventually
    promise.result().result.get
  }

  /**
   * Offer a request. When the response arrives, complete the promise passed as an argument.
   * @return whether the message could be enqueued or the corresponding queue overflowed
   */
  def offerRequest(
    target: Address,
    message: Message,
    timeoutMs: Int,
    promise: Promise[IncomingResponse],
    flow: ClientFlow,
    timeout: Long,
    unit: TimeUnit): Boolean = {
    checkClosed()
    if (!flow.isOpen)
      throw new IllegalStateException("Cannot send messages associated with a closed flow")
    checkTimeout(timeoutMs)
    val connector = getConnector(target)
    flow.markUse()
    val pendingResponse = new PendingResponse(promise, timeoutMs, connector, flow)
    connector.offerRequest(message, pendingResponse, timeout, unit)
  }

  def offerRequest(
    target: Address, message: Message, timeoutMs: Int, promise: Promise[IncomingResponse], flow: ClientFlow): Boolean =
    offerRequest(target, message, timeoutMs, promise, flow, 0, TimeUnit.NANOSECONDS)

  def offerRequest(
    target: Address, message: Message, timeoutMs: Int, promise: Promise[IncomingResponse], timeout: Long, unit: TimeUnit): Boolean =
    offerRequest(target, message, timeoutMs, promise, mainFlow, timeout, unit)

  def offerRequest(
    target: Address, message: Message, timeoutMs: Int, promise: Promise[IncomingResponse]): Boolean =
    offerRequest(target, message, timeoutMs, promise, mainFlow, 0, TimeUnit.NANOSECONDS)

  /**
   * Offer a message.
   * @return true if the message could be enqueued, false otherwise
   */
  def offerMessage(target: Address, message: Message, timeout: Long, unit: TimeUnit): Boolean = {
    checkClosed()
    val connector = getConnector(target)
    connector.offerMessage(message, timeout, unit)
  }

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
