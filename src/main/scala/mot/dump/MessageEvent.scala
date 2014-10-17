package mot.dump

import mot.Connection
import mot.message.MessageBase
import java.text.SimpleDateFormat
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.net.InetSocketAddress

object Direction extends Enumeration {
  val Incoming, Outgoing = Value
}

case class MessageEvent(timestampMs: Long, conn: Connection, direction: Direction.Value, message: MessageBase) {
  
  def print(os: OutputStream, sdf: SimpleDateFormat, showBody: Boolean, maxBodyLength: Int, showAttributes: Boolean) = {
    val arrow = direction match {
      case Direction.Incoming => '<'
      case Direction.Outgoing => '>'
    }
    val local = formatAddress(conn.localAddress)
    val remote = formatAddress(conn.remoteAddress)
    val firstLine = 
      s"${sdf.format(timestampMs)} ${conn.localName}[$local] $arrow ${conn.remoteName}[$remote] ${message.dump}\n"
    os.write(firstLine.getBytes(UTF_8))
    if (showAttributes) {
      for ((name, value) <- message.attributes) {
        val valueStr = new String(value, UTF_8)
        os.write(s"$name: $valueStr\n".getBytes(UTF_8))
      }
    }
    if (showBody && message.bodyLength > 0) {
      var remaining = maxBodyLength
      for (buffer <- message.body) {
        val show = math.min(remaining, buffer.limit)
        os.write(buffer.array, buffer.arrayOffset, show)
        remaining -= show
      }
      os.write('\n')
    }
  }
  
  def formatAddress(address: InetSocketAddress) = {
    address.getAddress.getHostAddress + ":" + address.getPort
  }
  
}