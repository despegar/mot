package mot.util

import java.util.concurrent.atomic.AtomicLong
import java.nio.charset.StandardCharsets
import java.net.Socket
import java.net.ServerSocket
import java.util.concurrent.locks.Lock
import io.netty.util.TimerTask
import io.netty.util.Timeout
import java.io.IOException
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration
import scala.concurrent.duration.Duration.Infinite

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

  def isAscii(string: String) = StandardCharsets.US_ASCII.newEncoder.canEncode(string)
  
  def withLock[A](lock: Lock)(thunk: => A): A = {
    lock.lock()
    try {
      thunk
    } finally {
      lock.unlock()
    }
  }
  
  def durationToString(duration: Duration) = duration match {
    case fd: FiniteDuration => fd.toString
    case id: Infinite => "∞"
  }
  
}