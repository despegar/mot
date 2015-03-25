package mot.util

import java.util.concurrent.atomic.AtomicBoolean
import java.net.Socket
import java.net.SocketException
import mot.Address
import java.net.InetSocketAddress

final class RichSocket(val impl: Socket) {

  private val closed = new AtomicBoolean

  def isClosed() = closed.get

  def closeOnce(): Boolean = {
    if (closed.compareAndSet(false, true)) {
      try {
        impl.close()
      } catch {
        case e: SocketException => // ignore
      }
      true
    } else {
      false
    }
  }
  
  val localAddress = Address.fromInetSocketAddress(impl.getLocalSocketAddress.asInstanceOf[InetSocketAddress])
  val remoteAddress = Address.fromInetSocketAddress(impl.getRemoteSocketAddress.asInstanceOf[InetSocketAddress])

}