package mot

import collection.JavaConversions._
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.promise
import Util.FunctionToRunnable
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

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
  val name: String,
  val responseMaxLength: Int = 100000,
  val queueSize: Int = 1000,
  val readerBufferSize: Int = 10000,
  val writerBufferSize: Int = 2000,
  val connectTimeout: Int = 3000,
  val uncaughtErrorHandler: UncaughtErrorHandler = LoggingErrorHandler,
  val pessimistic: Boolean = false) extends Logging {

  // TODO: GC unused connectors
  private[mot] val connectors = new ConcurrentHashMap[Target, ClientConnector]

  @volatile private var closed = false

  checkName()

  Context.clients.put(name, this)
  
  private def checkName() {
    if (!Util.isAscii(name))
      throw new IllegalArgumentException(s"Only US-ASCII characters are allowed in client name")
    val max = Protocol.PublisherNameMaxLength
    if (name.length > max)
      throw new IllegalArgumentException(s"Client name cannot be longer than $max characters")
  }

  private def getConnector(target: Target) = {
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
   * @return Some(future) of a response if the message could be enqueued or None if the corresponding queue overflowed
   */
  def offerRequest(target: Target, message: Message, timeout: Int) = {
    checkClosed()
    val connector = getConnector(target)
    bePessimistic(connector)
    val p = promise[Message]
    val now = System.nanoTime()
    if (connector.offer(message, Some(ResponsePromise(p, now, timeout))))
      Some(p.future)
    else
      None
  }
  
  /**
   * Send a request. Block until the message can be enqueued.
   * @return a future of a response
   */
  def sendRequest(target: Target, message: Message, timeout: Int) = {
    checkClosed()
    val connector = getConnector(target)
    bePessimistic(connector)
    val p = promise[Message]
    val now = System.nanoTime()
    connector.put(message, Some(ResponsePromise(p, now, timeout)))
    p.future
  }
  
  def sendRequestAndWait(target: Target, message: Message, timeout: Int) =
    Await.result(sendRequest(target, message, timeout), Duration(timeout, TimeUnit.MILLISECONDS))
  
  def sendMessage(target: Target, message: Message) = {
    checkClosed()
    val connector = getConnector(target)
    bePessimistic(connector)
    connector.put(message, None)
  }
  
  def offerMessage(target: Target, message: Message) = {
    checkClosed()
    val connector = getConnector(target)
    bePessimistic(connector)
    connector.offer(message, None)
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
    Context.clients.remove(name)
    connectors.values.foreach(_.close())
  }
  
}