package mot

import scala.concurrent.Promise

case class ResponsePromise(promise: Promise[Message], requestTime: Long, timeoutMs: Int) {
  def forget(e: Exception) = promise.tryFailure(e)
  def fulfill(m: Message) = promise.trySuccess(m)
  def delay() = System.nanoTime() - (requestTime + timeoutMs * 1000 * 1000)
}
