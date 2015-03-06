package mot.util

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import scala.util.Try
import java.util.concurrent.TimeUnit

/**
 * A kind of [[mot.util.Promise]] that can be completed only once.
 */
class UnaryPromise[A] extends Promise[A] {

  private val completionLatch = new CountDownLatch(1)
  private val _value = new AtomicReference[Option[A]](None) 
  
  val latch = completionLatch
    
  /** 
   *  The value of this Promise. If the promise is not completed the returned value will be `None`. If the promise is 
   *  completed the value will be `Some(Success(t))` if it contains a valid result, or `Some(Failure(error))` if it 
   *  contains an exception.
   */
  def value(): Option[A] = _value.get
  
  def tryComplete(result: A) = {
    val success = _value.compareAndSet(None, Some(result))
    completionLatch.countDown()
    success
  }
  
  def result(): A = {
    completionLatch.await()
    value.get
  }

  def result(timeout: Long, unit: TimeUnit): Option[A] = {
    completionLatch.await(timeout, unit)
    value
  }

}
