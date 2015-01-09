package mot

import mot.util.Promise

object DelayPromise extends Promise[Any] {

  @volatile var delay = 0
  
  def tryComplete(result: Any) = {
    Thread.sleep(delay)
    true
  }
  
}
