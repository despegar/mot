package mot.dump

import java.io.OutputStream
import java.text.SimpleDateFormat
import mot.impl.Connection

object Direction extends Enumeration {
  val Incoming, Outgoing = Value
}

trait Event {
    
  val timestampMs = System.currentTimeMillis()
  def direction: Direction.Value
  def conn: Connection
  def print(os: OutputStream, sdf: SimpleDateFormat, showBody: Boolean, maxBodyLength: Int, showAttributes: Boolean): Unit
  def protocol: String
    
  lazy val (fromAddress, fromName, toAddress, toName) = direction match {
      case Direction.Incoming => (conn.remoteAddress, conn.remoteName, conn.localAddress, conn.localName)
      case Direction.Outgoing => (conn.localAddress, conn.localName, conn.remoteAddress, conn.remoteName)
  }

}