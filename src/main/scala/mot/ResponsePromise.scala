package mot

import scala.concurrent.Promise

class ResponsePromise(val promise: Promise[Message], requestTime: Long, val timeoutMs: Int) {
  val expiration = {
    val timeoutNs = timeoutMs.toLong * 1000 * 1000
    requestTime + timeoutNs
  }
}
