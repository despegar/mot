package mot.impl

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import mot.util.UnaryPromise
import mot.util.Util
import mot.Limits

object Protocol {
  
  val ProtocolVersion = 1
  val HeartBeatInterval = Duration(5, TimeUnit.SECONDS)
  val HeartBeatIntervalNs = HeartBeatInterval.toNanos
    
  def checkName(name: String) {
    import Limits._
    if (!Util.isAscii(name))
      throw new IllegalArgumentException(s"Only US-ASCII characters are allowed in party name")
    if (name.length > PartyNameMaxLength)
      throw new IllegalArgumentException(s"Party name cannot be longer than $PartyNameMaxLength characters")
  }
  
}