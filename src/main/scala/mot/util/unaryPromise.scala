package mot.util

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration.Infinite
import scala.util.Try

class UnaryPromise[A] extends Promise[A] {

  private val completionLatch = new CountDownLatch(1)
  private val _value = new AtomicReference[Option[A]](None) 
  
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
  
  def result(): A = result(Duration.Inf).get

  def result(duration: Duration): Option[A] = {
    duration match {
      case fd: FiniteDuration => 
        completionLatch.await(duration.length, duration.unit)
      case inf: Infinite => 
        completionLatch.await()
    }
    value
  }
  
}

class UnaryFailingPromise[A] extends UnaryPromise[Try[A]] with FailingPromise[A]