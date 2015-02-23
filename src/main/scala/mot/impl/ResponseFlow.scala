package mot.impl

import mot.queue.LinkedBlockingMultiQueue

class ResponseFlow(
    connection: ServerConnection, val id: Int, val queue: LinkedBlockingMultiQueue[Int, OutgoingResponse]#SubQueue) {
  
  private var _lastUse = System.nanoTime()
  
  def markUse() = {
    _lastUse = System.nanoTime()
  }
  
  def lastUse() = _lastUse
  
  def isSaturated() =
    queue.size.toDouble / queue.capacity > connection.server.sendingQueueSaturationThreshold
  
  def isRecovered() =
    queue.size.toDouble / queue.capacity < connection.server.sendingQueueRecoveryThreshold
    
}