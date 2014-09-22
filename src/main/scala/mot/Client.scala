package mot

import collection.JavaConversions._
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.promise
import Util.FunctionToRunnable
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.util.control.NonFatal
import com.typesafe.scalalogging.slf4j.StrictLogging
import java.util.concurrent.ThreadFactory
import io.netty.util.HashedWheelTimer

/**
 * Thread model:
 * - One thread per target
 *
 * @param name the name that is advertised to subscriber as the sender of each message
 * @param queueSize length (in messages) of each queue (there is one queue per subscriber
 * @param bufferSize length (in bytes) of each sending buffer (there is one buffer per connection)
 * @param connectTimeout timeout used in the establishment of connections
 * @param uncaughtErrorHandler what to do if an uncaught exception terminates a thread
 * @param optimistic whether to accept requests when the underlying is known to be faulty
 */
class Client(
  val context: Context,
  val name: String,
  val responseMaxLength: Int = 100000,
  val queueSize: Int = 5000,
  val readerBufferSize: Int = 10000,
  val writerBufferSize: Int = 10000,
  val connectorGcSec: Int = 600,
  val pessimistic: Boolean = false) extends StrictLogging {

  val connectorGcMs = TimeUnit.SECONDS.toMillis(connectorGcSec)
  
  private[mot] val connectors = new ConcurrentHashMap[Address, ClientConnector]

  @volatile private var closed = false
  
  val expiratorThread = new Thread(connectorExpirator _, s"mot($name)-connector-expirator")

  Protocol.checkName(name)
  context.registerClient(this)
  expiratorThread.start()
    
  private[mot] val promiseExpirator = {
    val tf = new ThreadFactory {
      def newThread(r: Runnable) = new Thread(r, s"mot($name)-promise-expiratior")
    }
    new HashedWheelTimer(tf, 200 /* tick duration */, TimeUnit.MILLISECONDS, 1000 /* ticks per wheel */)
  }

  def connectorExpirator() = {
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
   * The future returned will block until the response arrived or the specified timeout expires.
   * @return Some(future) of a response if the message could be enqueued or None if the corresponding queue overflowed
   */
  def offerRequest(target: Address, message: Message, timeoutMs: Int) = {
    checkClosed()
    checkTimeout(timeoutMs)
    val connector = getConnector(target)
    bePessimistic(connector)
    val p = promise[Message]
    if (connector.offerRequest(message, new PendingResponse(p, timeoutMs, connector)))
      Some(p.future)
    else
      None
  }
  
  /**
   * Send a request. Block until the message can be enqueued.
   * The future returned will block until the response arrived or the specified timeout expires.
   * @return a future of a response
   */
  def sendRequest(target: Address, message: Message, timeoutMs: Int) = {
    checkClosed()
    checkTimeout(timeoutMs)
    val connector = getConnector(target)
    bePessimistic(connector)
    val p = promise[Message]
    connector.putRequest(message, new PendingResponse(p, timeoutMs, connector))
    p.future
  }
  
  /**
   * Send a request and block until the response arrives or the timeout expires.
   */
  def getResponse(target: Address, message: Message, timeoutMs: Int) =
    Await.result(sendRequest(target, message, timeoutMs), Duration.Inf /* future will timeout by itself */)
  
  def sendMessage(target: Address, message: Message) = {
    checkClosed()
    val connector = getConnector(target)
    bePessimistic(connector)
    connector.putMessage(message)
  }
  
  def offerMessage(target: Address, message: Message) = {
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

  def close() = {
    closed = true
    context.clients.remove(name)
    connectors.values.foreach(_.close())
    promiseExpirator.stop()
  }
  
}

