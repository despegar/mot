package mot

import mot.util.Promise

object NullPromise extends Promise[Any] {
  def tryComplete(result: Any) = true
}
