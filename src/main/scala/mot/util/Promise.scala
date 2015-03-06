package mot.util

/**
 * Promise is an object which can be completed with a value or failed with an exception.
 */
trait Promise[-A] {
  
  /** 
   *  Complete the promise with either an exception or a value.
   *  @param result Either the value or the exception to complete the promise with.
   */
  def complete(result: A): Unit = {
    if (!tryComplete(result)) 
      throw new IllegalStateException("Promise already completed.")
  }

  /** 
   *  Try to complete the promise with either a value or the exception.
   *  @return If the promise has already been completed returns `false`, or `true` otherwise.
   */
  def tryComplete(result: A): Boolean

}