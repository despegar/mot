package mot

import scala.concurrent.Promise
import java.util.concurrent.ScheduledFuture

case class PendingResponse(promise: Promise[Message], expiration: Long, expirationTask: ScheduledFuture[_])