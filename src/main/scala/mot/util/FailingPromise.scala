package mot.util

import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait FailingPromise[-A] extends Promise[Try[A]] {

  /** 
   *  Complete the promise with a value.
   *  @param v the value to complete the promise with.
   */
  def success(v: A) = complete(Success(v))

  /** 
   *  Try to complete the promise with a value.
   *  @return whether the promise has already been completed returns `false`, or `true` otherwise.
   */
  def trySuccess(value: A): Boolean = tryComplete(Success(value))

  /** 
   *  Complete the promise with an exception.
   *  @param t the throwable to complete the promise with.
   */
  def failure(t: Throwable) = complete(Failure(t))

  /** 
   *  Try to complete the promise with an exception.
   *  @return whether the promise has already been completed returns `false`, or `true` otherwise.
   */
  def tryFailure(t: Throwable): Boolean = tryComplete(Failure(t))
 
}