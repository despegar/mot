package mot.impl

import mot.util.Offerable

class ResponseFlow(connection: ServerConnection, val id: Int, val queue: Offerable[OutgoingResponse]) {
  
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