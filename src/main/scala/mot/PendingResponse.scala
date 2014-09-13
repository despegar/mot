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

  @volatile var expirationTask: ScheduledFuture[_] = _
  
  // Guarded by sentLock
  var mapReference: Option[PendingResponse.MapReference] = None

  def scheduleExpiration() = {
    expirationTask = connector.promiseExpirator.schedule(timeout _, timeoutMs, TimeUnit.MILLISECONDS)
  }

  def unscheduleExpiration() = {
    expirationTask.cancel(false /* mayInterruptIfRunning */ )
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
  
  def timeout(): Unit = {
    if (promise.tryFailure(new ResponseTimeoutException))
      connector.timeoutsCounter += 1
    withLock(sentLock) {
      mapReference.foreach(ref => ref.connection.pendingResponses.remove(ref.sequence))
    }
  }

  def fulfill(message: Message) = {
    unscheduleExpiration()
    promise.trySuccess(message)
  }

  def error(error: Exception) = {
    unscheduleExpiration()
    promise.tryFailure(error)
  }

}

object PendingResponse {
  case class MapReference(connection: ClientConnection, sequence: Int)
}
