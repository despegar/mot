package mot

import collection.JavaConversions._
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.promise
import Util.FunctionToRunnable
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.util.control.NonFatal

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
  val queueSize: Int = 1000,
  val readerBufferSize: Int = 10000,
  val writerBufferSize: Int = 2000,
  val connectTimeout: Int = 3000,
  val connectorGcSec: Int = 600,
  val pessimistic: Boolean = false) extends Logging {

  private[mot] val connectors = new ConcurrentHashMap[Address, ClientConnector]

  @volatile private var closed = false
  
  val expiratorThread = new Thread(connectorExpirator _, s"mot($name)-connector-expirator")

  Protocol.checkName(name)
  context.registerClient(this)
  expiratorThread.start()
  
  def connectorExpirator() = {
    try {
      val runDelayMs = 1000
      val connectorGcNs = connectorGcSec.toLong * 1000 * 1000 * 1000
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
   * @return Some(future) of a response if the message could be enqueued or None if the corresponding queue overflowed
   */
  def offerRequest(target: Address, message: Message, timeout: Int) = {
    checkClosed()
    val connector = getConnector(target)
    bePessimistic(connector)
    val p = promise[Message]
    if (connector.offerRequest(message, new PendingResponse(p, timeout, connector)))
      Some(p.future)
    else
      None
  }
  
  /**
   * Send a request. Block until the message can be enqueued.
   * @return a future of a response
   */
  def sendRequest(target: Address, message: Message, timeout: Int) = {
    checkClosed()
    val connector = getConnector(target)
    bePessimistic(connector)
    val p = promise[Message]
    connector.putRequest(message, new PendingResponse(p, timeout, connector))
    p.future
  }
  
  def sendRequestAndWait(target: Address, message: Message, timeout: Int) =
    Await.result(sendRequest(target, message, timeout), Duration(timeout, TimeUnit.MILLISECONDS))
  
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
  }
  
}