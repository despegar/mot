package mot

import lbmq.LinkedBlockingMultiQueue
import mot.impl.ServerConnection
import mot.impl.OutgoingResponse

/**
 * Instances of this class represent a server-side flow.
 *
 * @see [[mot.ClientFlow]]
 */
final class ServerFlow private[mot] (
  private val connection: ServerConnection,
  val id: Int) {

  private[mot] var queue: LinkedBlockingMultiQueue[Int, OutgoingResponse]#SubQueue = null
  
  private var _lastUse = System.nanoTime()

  private[mot] def markUse() = {
    _lastUse = System.nanoTime()
  }

  private[mot] def lastUse() = _lastUse

  @volatile private var terminated = false

  def isTerminated() = terminated

  private[mot] def terminate(): Unit = {
    terminated = true
  }

  def isSaturated() = {
    val capacity = queue.remainingCapacity + queue.size
    queue.size.toDouble / capacity > connection.server.sendingQueueSaturationThreshold
  }

  def isRecovered() = {
    val capacity = queue.remainingCapacity + queue.size
    queue.size.toDouble / capacity < connection.server.sendingQueueRecoveryThreshold
  }

}