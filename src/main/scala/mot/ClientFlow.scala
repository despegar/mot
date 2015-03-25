package mot

import scala.collection.JavaConversions._
import mot.impl.FlowNotification
import java.util.concurrent.atomic.AtomicBoolean
import com.typesafe.scalalogging.slf4j.StrictLogging

/**
 * A "client flow" is an abstraction created to enable selective flow control, which is a way to ask the server in the 
 * other side to stop sending responses of a certain kind. The need for selective flow control comes when the client 
 * can receive back-pressure using some subset of the responses. Proxy servers are a prototypical case: when sending
 * the responses to the front-end, some clients can slow down (the slowness can come from the themselves or from the 
 * network) and the proxy must either buffer the data, discard it, or tell the back-end to stop sending it. 
 *
 * If there is no need to be selective, there is no need of explicit flow control, as it is simpler to rely on TCP for 
 * that (not reading, and causing "zero window" notifications). This is the most common case.
 * 
 * Instances of this class are created by the Mot client, and are used opaquely: each request is associated with a
 * flow (which defaults to the main flow). The client can then use the flows (of which it must keep references) to
 * stop responses selectively: "closing" a flow instructs the server to do not send the responses of the messages 
 * that were associated with that particular flow. If flow control is not used, there is only one instance of this 
 * class, which is always created (the "main flow" with id 0).
 * 
 * This mechanism only applies to responses. Requests do not need it, as it is always possible to respond them with
 * an application-level indication to stop. This is not the case with responses, which could only be discarded.
 * (In the proxy case, additionally, it would be impossible for the front-end to know in advance which is the final 
 * target of a request).
 * 
 * It is also worth mentioning that no response is stopped half-sent: Mot is a small-message protocol and each message 
 * (that cannot use more than one frame) is always sent entirely o not at all.
 *
 * @see [[mot.protocol.FlowControlFrame]]  
 */
final class ClientFlow private[mot](val id: Int, val client: Client) extends StrictLogging {

  private val status = new AtomicBoolean(true) // flows start open

  /**
   * Close the flow. A frame is set telling the server in the other side to stop sending responses to the messages 
   * associated with this flow.
   * @return {@code true} if the command succeeded (because the flow was open). 
   */
  def closeFlow() = updateFlow(false)
  
  /**
   * Open the flow. A frame is set telling the server in the other side to start sending responses to the messages 
   * associated with this flow again.
   * @return {@code true} if the command succeeded (because the flow was closed). 
   */
  def openFlow() = updateFlow(true)
  
  private def updateFlow(newStatus: Boolean): Boolean = {
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

  /**
   * Return whether this flow is open.
   */
  def isOpen() = status.get

  override def toString() = s"ClientFlow(id=$id,client=$client)"

}

