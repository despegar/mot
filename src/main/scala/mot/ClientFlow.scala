package mot

import scala.collection.JavaConversions._
import mot.impl.FlowNotification
import java.util.concurrent.atomic.AtomicBoolean
import com.typesafe.scalalogging.slf4j.StrictLogging

/**
 * Objects of this class are created 
 */
class ClientFlow private[mot](val id: Int, val client: Client) extends StrictLogging {

  private val status = new AtomicBoolean(true)

  @volatile private var _lastUse = -1L
  
  def closeFlow() = updateFlow(false)
  def openFlow() = updateFlow(true)
  
  def updateFlow(newStatus: Boolean): Boolean = {
    val success = status.compareAndSet(!newStatus, newStatus)
    if (success) {
      for (connector <- client.connectors.values) {
        val enqueued = connector.flowControlQueue.offer(FlowNotification(id, status = newStatus))
        if (!enqueued) {
          // The flow control queue is of higher priority than the normal message queue, so and an overflow here means
          // than everything is already blocked, and there is not much that can be done
          logger.debug("could not send flow control message because of flow control queue overflow")
        }
      }
    }
    success
  }

  def isOpen() = status.get

  def markUse(): Unit = {
    _lastUse = System.nanoTime()
  }
  
  def lastUse() = _lastUse
  
  override def toString() = s"ClientFlow(id=$id,client=$client)"

}

