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
import mot.Address
import mot.util.Util.RichAtomicLong

final class PendingResponse(
  val promise: Promise[IncomingResponse],
  val timeoutMs: Int,
  val connector: ClientConnector,
  val flow: ClientFlow) {

  val requestId = connector.requestCounter.incrementAndGet()
  
  // Check null at construction to avoid an exception in the timer thread
  if (promise == null)
    throw new NullPointerException("promise cannot be null")
  
  @volatile var expirationTask: Timeout = _

  def scheduleExpiration() = {
    expirationTask = connector.client.promiseExpirator.newTimeout(timeout _, timeoutMs, TimeUnit.MILLISECONDS)
  }

  private def timeout(): Unit = {
    val remoteAddress = connector.target // use unresolved address
    if (promise.tryComplete(IncomingResponse(remoteAddress, None, Failure(new ResponseTimeoutException), flow))) {
      connector.timeoutsCounter.lazyIncrement() // valid because only one thread expires
    }
    connector.pendingResponses.remove(requestId)
  }

  def fulfill(conn: ClientConnection, message: Message) = {
    // Can occur even before the expiration is scheduled
    val exp = expirationTask // dereference volatile once
    if (exp != null)
      exp.cancel()
    promise.tryComplete(IncomingResponse(conn.remoteAddress, Some(conn.localAddress), Success(message), flow))
  }

  def error(conn: ClientConnection, error: Throwable) = {
    // Can occur even before the expiration is scheduled
    val exp = expirationTask // dereference volatile once
    if (exp != null)
      exp.cancel()
    promise.tryComplete(IncomingResponse(conn.remoteAddress, Some(conn.localAddress), Failure(error), flow))
  }

}
