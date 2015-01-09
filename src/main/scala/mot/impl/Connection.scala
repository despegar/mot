package mot.impl

import java.net.InetSocketAddress
import java.net.Socket
import mot.Address

trait Connection {
  
  def localAddress: Address
  def remoteAddress: Address
  
  def localName: String
  def remoteName: String
  
  def prepareSocket(socket: Socket) {
    socket.setSoTimeout(Protocol.HeartBeatInterval.toMillis.toInt * 2)
    socket.setTcpNoDelay(true)
  }

}