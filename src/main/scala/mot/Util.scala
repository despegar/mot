package mot

import java.net.Socket
import java.net.SocketException
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.charset.StandardCharsets

object Util {

  implicit class FunctionToRunnable(f: () => Unit) extends Runnable {
    def run() = f()
  }
  
  implicit class ByteToBoolean(val i: Byte) extends AnyVal {
    def toBoolean = i != 0
  }

  implicit class BooleanToByte(val b: Boolean) extends AnyVal {
    def toByte: Byte = if (b) 1 else 0
  }
  
  def closeSocket(socket: Socket) {
    try {
      socket.close()
    } catch {
      case e: SocketException => // ignore
    }
  }

  def closeSocket(socket: ServerSocket) {
    try {
      socket.close()
    } catch {
      case e: SocketException => // ignore
    }
  }

  def pow(b: Int, exp: Int) = math.pow(b, exp).toLong

  def retryConditionally[A](op: => A, finish: AtomicBoolean, maxDelay: Int = 1000)(catchFn: PartialFunction[Throwable, Unit]) = {
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
  
}