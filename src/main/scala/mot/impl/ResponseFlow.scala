package mot.impl

import mot.queue.LinkedBlockingMultiQueue

/**
 * Instances of this class represent a server-side flow.
 * 
 * @see [[mot.ClientFlow]]
 */
class ResponseFlow private[mot] (
    connection: ServerConnection, val id: Int, val queue: LinkedBlockingMultiQueue[Int, OutgoingResponse]#SubQueue) {
  
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