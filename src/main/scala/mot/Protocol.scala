package mot

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import mot.util.UnaryPromise

object Protocol {
  
  val ProtocolVersion = 1
  val HeartBeatInterval = Duration(5, TimeUnit.SECONDS)
  val HeartBeatIntervalNs = HeartBeatInterval.toNanos
  
  val PartyNameMaxLength = Byte.MaxValue
  val AttributeNameMaxLength = Byte.MaxValue
  val AttributeValueMaxLength = Short.MaxValue
  val BodyMaxLength = Int.MaxValue
    
  def checkName(name: String) {
    if (!Util.isAscii(name))
      throw new IllegalArgumentException(s"Only US-ASCII characters are allowed in party name")
    if (name.length > PartyNameMaxLength)
      throw new IllegalArgumentException(s"Party name cannot be longer than $PartyNameMaxLength characters")
  }
  
  def wait[A](promise: UnaryPromise[A], stop: () => Boolean) = {
    var value: Option[A] = None
    while (value.isEmpty && !stop()) {
      value = promise.result(Duration(100, TimeUnit.MILLISECONDS))
    }
    value
  }
  
}