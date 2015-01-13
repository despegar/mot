package mot.dump

import mot.impl.Connection
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.nio.charset.StandardCharsets.US_ASCII

object Operation extends Enumeration {
  val FailedNameResolution, FailedAttempt, Creation, Close = Value
}

case class ConnectionEvent(conn: Connection, direction: Direction.Value, operation: Operation.Value, cause: String = "") 
    extends Event {

  def print(os: OutputStream, sdf: SimpleDateFormat, showBody: Boolean, maxBodyLen: Int, showAttr: Boolean) = {
    val ts = sdf.format(timestampMs) 
    val line = s"$ts [tcp] $fromName[$fromAddress] > $toName[$toAddress] ${operation}, cause [$cause]\n"
    os.write(line.getBytes(US_ASCII))
  }
    
}