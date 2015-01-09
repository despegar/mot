package mot.impl

import java.util.concurrent.TimeUnit
import io.netty.util.Timeout
import mot.Message
import mot.ResponseTimeoutException
import mot.util.Promise
import scala.util.Failure
import scala.util.Success
import mot.ClientFlow
import mot.IncomingResponse
import mot.util.Util.FunctionToTimerTask

class PendingResponse(
  val promise: Promise[IncomingResponse],
  val timeoutMs: Int,
  val connector: ClientConnector,
  val flow: ClientFlow) {

  val requestId = connector.requestCounter.incrementAndGet()
  
  // Check null at construction to avoid an exception in the timer thread
  if (promise == null)
    throw new NullPointerException("promise cannot be null")
  
  var expirationTask: Timeout = _

  def scheduleExpiration() = {
    expirationTask = connector.client.promiseExpirator.newTimeout(timeout _, timeoutMs, TimeUnit.MILLISECONDS)
  }

  private def timeout(): Unit = {
    if (promise.tryComplete(IncomingResponse(Failure(new ResponseTimeoutException), flow))) {
      connector.timeoutsCounter += 1
    }
    connector.pendingResponses.remove(requestId)
  }

  def fulfill(message: Message) = synchronized {
    expirationTask.cancel()
    promise.tryComplete(IncomingResponse(Success(message), flow))
  }

  def error(error: Exception) = synchronized {
    // Errors can occur at any time, even before the expiration is scheduled
    if (expirationTask != null)
      expirationTask.cancel()
    promise.tryComplete(IncomingResponse(Failure(error), flow))
  }

}
