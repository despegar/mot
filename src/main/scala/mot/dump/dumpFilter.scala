package mot.dump

import mot.message.MessageBase
import mot.Connection
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.regex.Pattern
import mot.message.MessageType

trait Filter {

  def filter(event: MessageEvent): Boolean

  def filterAddress(event: MessageEvent, side: Side.Value, filter: InetSocketAddress => Boolean) = {
    import Direction._
    side match {
      case Side.Dest => event.direction match {
        case Incoming => filter(event.conn.localAddress)
        case Outgoing => filter(event.conn.remoteAddress)
      }
      case Side.Source => event.direction match {
        case Incoming => filter(event.conn.remoteAddress)
        case Outgoing => filter(event.conn.localAddress)
      }
      case Side.SourceOrDest =>
        filter(event.conn.remoteAddress) || filter(event.conn.localAddress)
    }
  }

}

case class Disjunction(left: Filter, right: Filter) extends Filter {
  def filter(event: MessageEvent) = left.filter(event) || right.filter(event)
}

case class Conjunction(left: Filter, right: Filter) extends Filter {
  def filter(event: MessageEvent) = left.filter(event) && right.filter(event)
}

case class Negation(part: Filter) extends Filter {
  def filter(event: MessageEvent) = !part.filter(event)
}

case class MessageTypeFilter(messageType: MessageType.Value) extends Filter {
  def filter(event: MessageEvent) = event.message.messageType == messageType
}

case class Port(side: Side.Value, port: Int) extends Filter {
  def filter(event: MessageEvent) = filterAddress(event, side, _.getPort == port)
}

case class Host(side: Side.Value, host: String) extends Filter {
  def filter(event: MessageEvent) = filterAddress(event, side, _.getAddress.getHostAddress == host)
}

object Side extends Enumeration {
  val Source, Dest, SourceOrDest = Value
}

case class DirectionFilter(direction: Direction.Value) extends Filter {
  def filter(event: MessageEvent) = event.direction == direction
}

case class LengthLess(length: Int) extends Filter {
  def filter(event: MessageEvent) = event.message.bodyLength < length
}

case class LengthGreater(length: Int) extends Filter {
  def filter(event: MessageEvent) = event.message.bodyLength > length
}

case class LengthLessEqual(length: Int) extends Filter {
  def filter(event: MessageEvent) = event.message.bodyLength <= length
}

case class LengthGreaterEqual(length: Int) extends Filter {
  def filter(event: MessageEvent) = event.message.bodyLength >= length
}

case class AttributePresence(name: String) extends Filter {
  def filter(event: MessageEvent) = event.message.attributes.exists(_._1 == name)
}

case class AttributeValue(name: String, value: String) extends Filter {
  def filter(event: MessageEvent) = 
    event.message.attributes.exists { case (n, v) => n == name && new String(v, UTF_8) == value }
}

case class AttributeRegex(name: String, regex: String) extends Filter {
  val pattern = Pattern.compile(regex)
  def filter(event: MessageEvent) = 
    event.message.attributes.exists { case (n, v) => n == name && pattern.matcher(new String(v, UTF_8)).matches() }
}

object AllFilter extends Filter {
  def filter(event: MessageEvent) = true
}

