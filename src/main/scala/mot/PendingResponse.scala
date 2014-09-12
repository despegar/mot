package mot

import scala.concurrent.Promise
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit
import Util.FunctionToRunnable
import Util.withLock

class PendingResponse(val promise: Promise[Message], val timeoutMs: Int, val connector: ClientConnector) extends Logging {

  private val sentLock = new ReentrantLock

  @volatile var expirationTask: Option[ScheduledFuture[_]] = None
  
  // Guarded by sentLock
  var mapReference: Option[PendingResponse.MapReference] = None

  def scheduleExpiration() = {
    expirationTask = Some(connector.promiseExpirator.schedule(timeout _, timeoutMs, TimeUnit.MILLISECONDS))
  }

  def unscheduleExpiration() = {
    expirationTask.foreach(_.cancel(false /* mayInterruptIfRunning */ ))
    expirationTask = None
  }

  def markSent(connection: ClientConnection, sequence: Int) = {
    withLock(sentLock) {
      if (promise.isCompleted) {
        false
      } else {
        mapReference = Some(PendingResponse.MapReference(connection, sequence))
        connection.pendingResponses.put(sequence, this)
        true
      }
    }
  }

  def fulfill(message: Message): Unit = {
    unscheduleExpiration()
    if (promise.trySuccess(message)) {
      withLock(sentLock)(mapReference.get.connection.connector.responsesReceivedCounter += 1)
    }
  }

  def error(error: Exception) = {
    unscheduleExpiration()
    promise.tryFailure(error)
  }
  
  def timeout(): Unit = {
    if (promise.tryFailure(new ResponseTimeoutException))
      connector.timeoutsCounter += 1
    withLock(sentLock) {
      mapReference.foreach(ref => ref.connection.pendingResponses.remove(ref.sequence))
    }
  }

}

object PendingResponse {
  case class MapReference(connection: ClientConnection, sequence: Int)
}
