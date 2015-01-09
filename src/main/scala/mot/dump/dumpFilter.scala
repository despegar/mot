package mot.dump

import mot.protocol.Frame
import mot.impl.Connection
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.regex.Pattern
import mot.protocol.AttributesSupport
import mot.Address

trait Filter {

  def filter(event: Event): Boolean

  def filterAddress(event: Event, side: Side.Value, filter: Address => Boolean) = {
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
  def filter(event: Event) = left.filter(event) || right.filter(event)
}

case class Conjunction(left: Filter, right: Filter) extends Filter {
  def filter(event: Event) = left.filter(event) && right.filter(event)
}

case class Negation(part: Filter) extends Filter {
  def filter(event: Event) = !part.filter(event)
}

case class MessageTypeFilter(messageType: Byte) extends Filter {
  def filter(event: Event) = event match {
    case me: MessageEvent => me.message.messageType == messageType
    case _ => false
  }
}

case class Port(side: Side.Value, port: Int) extends Filter {
  def filter(event: Event) = filterAddress(event, side, _.port == port)
}

case class Host(side: Side.Value, host: String) extends Filter {
  def filter(event: Event) = filterAddress(event, side, _.host == host)
}

object Side extends Enumeration {
  val Source, Dest, SourceOrDest = Value
}

case class DirectionFilter(direction: Direction.Value) extends Filter {
  def filter(event: Event) = event match {
    case me: MessageEvent => me.direction == direction
    case _ => false
  }
}

case class LengthLess(length: Int) extends Filter {
  def filter(event: Event) = event match {
    case me: MessageEvent => me.message.length < length
    case _ => false
  }
}

case class LengthGreater(length: Int) extends Filter {
  def filter(event: Event) = event match {
    case me: MessageEvent => me.message.length > length
    case _ => false
  }
}

case class LengthLessEqual(length: Int) extends Filter {
  def filter(event: Event) = event match {
    case me: MessageEvent => me.message.length <= length
    case _ => false
  }
}

case class LengthGreaterEqual(length: Int) extends Filter {
  def filter(event: Event) = event match {
    case me: MessageEvent => me.message.length >= length
    case _ => false
  }
}

case class AttributePresence(name: String) extends Filter {
  def filter(event: Event) = event match {
    case me: MessageEvent =>
      me.message match {
        case am: AttributesSupport => am.attributes.exists(_._1 == name)
        case _ => false
      }
    case _ =>
      false
  }
}

case class AttributeValue(name: String, value: String) extends Filter {
  def filter(event: Event) = event match {
    case me: MessageEvent =>
      me.message match {
        case am: AttributesSupport => am.attributes.exists { case (n, v) => n == name && v.asString(UTF_8) == value }
        case _ => false
      }
    case _ =>
      false
  }
}

case class AttributeRegex(name: String, regex: String) extends Filter {
  val pattern = Pattern.compile(regex)
  def filter(event: Event) = event match {
    case me: MessageEvent =>
      me.message match {
        case am: AttributesSupport =>
          am.attributes.exists { case (n, v) => n == name && pattern.matcher(v.asString(UTF_8)).matches() }
        case _ =>
          false
      }
    case _ =>
      false
  }
}

object AllFilter extends Filter {
  def filter(event: Event) = true
}

