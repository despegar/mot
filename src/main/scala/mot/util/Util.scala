package mot.util

import java.util.concurrent.atomic.AtomicLong
import java.nio.charset.StandardCharsets
import java.net.Socket
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import io.netty.util.TimerTask
import io.netty.util.Timeout
import java.io.IOException

object Util {

  implicit class CeilingDivider(val n: Long) extends AnyVal {
    def /^(d: Long) = (n + d - 1) / d
  }
  
  implicit def atomicLong2Getter(al: AtomicLong): () => Long = al.get
  
  val bytes8 = 1
  val bytes16 = 2
  val bytes24 = 3
  val bytes31 = 4
  val bytes32 = 4
  
  implicit class FunctionToRunnable(f: () => Unit) extends Runnable {
    def run() = f()
  }
  
  implicit class FunctionToTimerTask(f: () => Unit) extends TimerTask {
    def run(t: Timeout) = f()
  }
  
  implicit class ByteToBoolean(val i: Byte) extends AnyVal {
    def toBoolean = i != 0
  }

  implicit class BooleanToByte(val b: Boolean) extends AnyVal {
    def toByte: Byte = if (b) 1 else 0
  }
  
  def closeSocket(socket: Socket): Unit = {
    try {
      socket.close()
    } catch {
      case e: IOException => // ignore
    }
  }

  def closeSocket(socket: ServerSocket): Unit = {
    try {
      socket.close()
    } catch {
      case e: IOException => // ignore
    }
  }

  def pow(b: Int, exp: Int) = math.pow(b, exp).toLong

  def retryConditionally[A]
      (op: => A, finish: AtomicBoolean, maxDelay: Int = 1000)(catchFn: PartialFunction[Throwable, Unit]) = {
    var res: Option[A] = None
    var reachedMax = false
    var i = 0
    do {
      // Avoid calculating theoretical exponential wait when the threshold wait has been reached, 
      // to avoid integer overflow
      val sleep = if (reachedMax) {
        maxDelay
      } else {
        val calc = 20L * (Util.pow(2, i) - 1)
        if (calc >= maxDelay) {
          reachedMax = true
          maxDelay
        } else {
          i += 1
          calc
        }
      }
      Thread.sleep(sleep)
      res = try {
        val res = op // by name execution
        Some(res)
      } catch {
        catchFn.andThen(_ => None)
      }
    } while (!finish.get && res.isEmpty)
    res
  }

  def isAscii(string: String) = StandardCharsets.US_ASCII.newEncoder.canEncode(string)
  
  def withLock[A](lock: Lock)(thunk: => A): A = {
    lock.lock()
    try {
      thunk
    } finally {
      lock.unlock()
    }
  }
  
}