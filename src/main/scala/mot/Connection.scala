package mot

import java.net.InetSocketAddress

trait Connection {
  def localAddress: InetSocketAddress
  def remoteAddress: InetSocketAddress
}