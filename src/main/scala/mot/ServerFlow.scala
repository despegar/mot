package mot

import mot.queue.LinkedBlockingMultiQueue
import mot.impl.ServerConnection
import mot.impl.OutgoingResponse

/**
 * Instances of this class represent a server-side flow.
 * 
 * @see [[mot.ClientFlow]]
 */
class ServerFlow private[mot] (
    private val connection: ServerConnection, 
    val id: Int, 
    private [mot] val queue: LinkedBlockingMultiQueue[Int, OutgoingResponse]#SubQueue) {
  
  private var _lastUse = System.nanoTime()
  
  private[mot] def markUse() = {
    _lastUse = System.nanoTime()
  }
  
  private[mot] def lastUse() = _lastUse
  
  def isSaturated() =
    queue.size.toDouble / queue.capacity > connection.server.sendingQueueSaturationThreshold
  
  def isRecovered() =
    queue.size.toDouble / queue.capacity < connection.server.sendingQueueRecoveryThreshold
    
}