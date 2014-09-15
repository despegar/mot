package mot

import scala.concurrent.Promise
import com.typesafe.scalalogging.slf4j.Logging
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit
import Util.FunctionToRunnable
import Util.withLock
import io.netty.util.Timeout
import io.netty.util.TimerTask

class PendingResponse(val promise: Promise[Message], val timeoutMs: Int, val connector: ClientConnector) extends Logging {

  private val sentLock = new ReentrantLock

  @volatile var expirationTask: Timeout = _
  
  // Guarded by sentLock
  var mapReference: Option[PendingResponse.MapReference] = None

  def scheduleExpiration() = {
    val timerTask = new TimerTask {
      def run(t: Timeout) = timeout()
    }
    expirationTask = connector.promiseExpirator.newTimeout(timerTask, timeoutMs, TimeUnit.MILLISECONDS)
  }

  def unscheduleExpiration() = {
    expirationTask.cancel()
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
