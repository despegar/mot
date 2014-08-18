package mot

import scala.concurrent.Promise

case class ResponsePromise(promise: Promise[Message], requestTime: Long, timeoutMs: Int) {
  val timeoutNs = timeoutMs.toLong * 1000 * 1000
  def forget(e: Exception) = promise.tryFailure(e)
  def fulfill(m: Message) = promise.trySuccess(m)
  def delay(now: Long) = now - (requestTime + timeoutNs)
  def isExpired(now: Long) = delay(now) > 0
}
