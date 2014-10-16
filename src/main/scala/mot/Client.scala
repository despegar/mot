package mot

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.util.control.NonFatal
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.util.HashedWheelTimer
import mot.Util.FunctionToRunnable
import mot.util.UnaryFailingPromise
import mot.util.FailingPromise

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
  val responseMaxLength: Int = 100000,
  val sendingQueueSize: Int = 5000,
  val readerBufferSize: Int = 10000,
  val writerBufferSize: Int = 5000,
  val connectorGcSec: Int = 600,
  val pessimistic: Boolean = false) extends StrictLogging {

  private val connectorGcMs = TimeUnit.SECONDS.toMillis(connectorGcSec)

  private[mot] val connectors = new ConcurrentHashMap[Address, ClientConnector]

  @volatile private var closed = false

  private val expiratorThread = new Thread(connectorExpirator _, s"mot($name)-connector-expirator")

  Protocol.checkName(name)
  context.registerClient(this)
  expiratorThread.start()

  private[mot] val promiseExpirator = {
    val tf = new ThreadFactory {
      def newThread(r: Runnable) = new Thread(r, s"mot($name)-promise-expiratior")
    }
    new HashedWheelTimer(tf, 200 /* tick duration */ , TimeUnit.MILLISECONDS, 1000 /* ticks per wheel */ )
  }

  private def connectorExpirator() = {
    try {
      val runDelayMs = 1000
      val connectorGcNs = TimeUnit.SECONDS.toNanos(connectorGcSec)
      while (!closed) {
        val threshold = System.nanoTime() - connectorGcNs
        val it = connectors.entrySet.iterator
        while (it.hasNext) {
          val entry = it.next
          val (target, connector) = (entry.getKey, entry.getValue)
          if (connector.lastUse < threshold) {
            it.remove()
            connector.close()
          }
        }
        Thread.sleep(runDelayMs)
      }
    } catch {
      case NonFatal(e) => context.uncaughtErrorHandler.handle(e)
    }
  }

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
    connector
  }

  /**
   * Offer a request. Succeed only if the message can be enqueued immediately.
   * When the response arrives, complete the promise passed as an argument.
   * @return whether the message could be enqueued or the corresponding queue overflowed
   */
  def offerRequest(target: Address, message: Message, timeoutMs: Int, promise: FailingPromise[Message]): Boolean = {
    checkClosed()
    checkTimeout(timeoutMs)
    val connector = getConnector(target)
    bePessimistic(connector)
    connector.offerRequest(message, new PendingResponse(promise, timeoutMs, connector))
  }  
  
  /**
   * Send a request. Block until the message can be enqueued.
   * When the response arrives, complete the promise passed as an argument.
   */
  def sendRequest(target: Address, message: Message, timeoutMs: Int, promise: FailingPromise[Message]): Unit = {
    checkClosed()
    checkTimeout(timeoutMs)
    val connector = getConnector(target)
    bePessimistic(connector)
    connector.putRequest(message, new PendingResponse(promise, timeoutMs, connector))
  }

  /**
   * Send a request and block until the response arrives or the timeout expires.
   */
  def getResponse(target: Address, message: Message, timeoutMs: Int): Message = {
    val promise = new UnaryFailingPromise[Message]
    sendRequest(target, message, timeoutMs, promise)
    // Block forever at the promise level, because Mot's timeout will make it fail eventually
    promise.result().get
  }

  /**
   * Send a message. Block until it can be enqueued.
   */
  def sendMessage(target: Address, message: Message): Unit = {
    checkClosed()
    val connector = getConnector(target)
    bePessimistic(connector)
    connector.putMessage(message)
  }
  
  /**
   * Offer a message. Succeed only if it can be enqueued immediately.
   * @return true if the message could be enqueued, false otherwise
   */
  def offerMessage(target: Address, message: Message): Boolean = {
    checkClosed()
    val connector = getConnector(target)
    bePessimistic(connector)
    connector.offerMessage(message)
  }

  private def checkTimeout(timeoutMs: Int) = {
    if (timeoutMs > connectorGcMs)
      throw new IllegalArgumentException(s"Request timeout cannot be longer than client GC time ($connectorGcMs sec.)")
  }

  private def bePessimistic(connector: ClientConnector) = {
    if (pessimistic) {
      connector.lastConnectingError.foreach { e =>
        throw new ErrorStateException(e)
      }
    }
  }

  private def checkClosed() = {
    if (closed)
      throw new ClientClosedException
  }

  /**
   * Close the client. Calling this method terminates all threads and connections.
   */
  def close(): Unit = {
    closed = true
    context.clients.remove(name)
    connectors.values.foreach(_.close())
    promiseExpirator.stop()
  }

}	

