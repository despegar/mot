package mot.impl

import mot.queue.LinkedBlockingMultiQueue
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import com.typesafe.scalalogging.slf4j.StrictLogging
import mot.util.Util.FunctionToRunnable
import scala.util.control.NonFatal
import mot.ServerFlow

class ServerFlows(connection: ServerConnection) extends StrictLogging {

  val multiQueue = new LinkedBlockingMultiQueue[Int, OutgoingResponse](connection.server.maxQueueSize)
  val flows = new ConcurrentHashMap[Int, ServerFlow]

  def totalSize() = multiQueue.totalSize

  def getOrCreateFlow(flowId: Int): ServerFlow = synchronized {
    val (subQueue, created) = multiQueue.getOrCreateSubQueue(flowId)
    if (created) {
      val newFlow = new ServerFlow(connection, flowId, subQueue)
      flows.put(flowId, newFlow)
      newFlow
    } else {
      val flow = flows.get(flowId)
      flow.markUse()
      flow
    }
  }

  def flow(flowId: Int) = Option(flows.get(flowId))

  def updateFlow(flowId: Int, open: Boolean) = {
    // Updates to unknown flows are ignored
    for (flow <- flow(flowId))
      flow.queue.enable(open)
  }

  def removeOldFlows(): Unit = {
    val threshold = System.nanoTime() - ResponseFlows.flowGc.toNanos
    val it = flows.entrySet.iterator
    while (it.hasNext) {
      val flow = it.next().getValue
      if (flow.lastUse < threshold) {
        logger.debug(s"Expiring flow ${flow.id} after ${ResponseFlows.flowGc} of inactivity")
        // order is important
        it.remove()
        flow.terminate()
        multiQueue.removeSubQueue(flow.id)
      }
    }
  }

}

object ResponseFlows {
  val flowGc = Duration(5, TimeUnit.MINUTES)
}
