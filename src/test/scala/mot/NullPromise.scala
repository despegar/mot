package mot

import scala.util.Try
import mot.util.FailingPromise

object NullPromise extends FailingPromise[Any] {
  def tryComplete(result: Try[Any]) = true
}
