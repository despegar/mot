package mot.util

import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import mot.IncomingResponse

/**
 * A Promise that will enqueue the eventual value. It is guaranteed that, for each promise, at most one value will
 * be enqueued.
 * This class can be useful for doing flow control on the threads that complete promises, as they will proceed only if
 * there is space in the supplied queue.
 */
abstract class AbstractQueuePromise[A](val queue: BlockingQueue[A]) extends Promise[IncomingResponse] {
  
  private val completed = new AtomicBoolean
  
  def isCompleted() = completed.get
 
  def decorate(result: IncomingResponse): A
  
  /**
   * Try to complete the promise. This method will block if there is no space in the queue
   */
  def tryComplete(result: IncomingResponse) = {
    val success = completed.compareAndSet(false, true)
    if (success)
      queue.put(decorate(result))
    success
  }

}
