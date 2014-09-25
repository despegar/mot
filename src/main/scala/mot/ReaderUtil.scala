package mot

import java.net.Socket

object ReaderUtil {

  def prepareSocket(socket: Socket) {
    socket.setSoTimeout(Protocol.HeartBeatInterval * 2)
    socket.setTcpNoDelay(true)
  }

}
