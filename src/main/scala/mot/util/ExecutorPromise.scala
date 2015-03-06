package mot.util

import java.util.concurrent.Executor
import mot.IncomingResponse
import java.util.concurrent.atomic.AtomicBoolean
import mot.util.Util.FunctionToRunnable

/**
 * A type of [[mot.util.Promise]] that submits a function to an executor when the promise is completed. 
 */
class ExecutorPromise(val executor: Executor, callback: IncomingResponse => Unit) extends Promise[IncomingResponse] {

  private val completed = new AtomicBoolean
  
  def isCompleted() = completed.get
 
  /**
   * Try to complete the promise. This method will block if there is no space in the queue
   */
  def tryComplete(result: IncomingResponse) = {
    val success = completed.compareAndSet(false, true)
    if (success)
      executor.execute(() => callback(result))
    success
  }
   
}