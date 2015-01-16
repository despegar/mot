package mot.dump

import mot.protocol.AttributesSupport
import java.nio.charset.StandardCharsets.UTF_8
import java.util.regex.Pattern

object Filters {

  case class Disj(left: Filter, right: Filter) extends Filter {
    def filter(event: Event) = left.filter(event) || right.filter(event)
  }

  case class Conj(left: Filter, right: Filter) extends Filter {
    def filter(event: Event) = left.filter(event) && right.filter(event)
  }

  case class Neg(part: Filter) extends Filter {
    def filter(event: Event) = !part.filter(event)
  }

  case class Type(messageType: Byte) extends Filter {
    def filter(event: Event) = filterMotEvent(event)(_.message.messageType == messageType)
  }
  
  case class Protocol(protocol: String) extends Filter {
    def filter(event: Event) = event.protocol == protocol
  }

  case class Port(side: Side.Value, port: Int) extends Filter {
    def filter(event: Event) = filterAddress(event, side)(_.port == port)
  }

  case class Host(side: Side.Value, host: String) extends Filter {
    def filter(event: Event) = filterAddress(event, side)(_.host == host)
  }

  object Side extends Enumeration {
    val Source, Dest, Any = Value
  }

  case class Dir(direction: Direction.Value) extends Filter {
    def filter(event: Event) = filterMotEvent(event)(_.direction == direction)
  }

  case class LengthLess(length: Int) extends Filter {
    def filter(event: Event) = filterMotEvent(event)(_.message.length < length)
  }

  case class LengthGreater(length: Int) extends Filter {
    def filter(event: Event) = filterMotEvent(event)(_.message.length > length)
  }

  case class LengthLessEqual(length: Int) extends Filter {
    def filter(event: Event) = filterMotEvent(event)(_.message.length <= length)
  }

  case class LengthGreaterEqual(length: Int) extends Filter {
    def filter(event: Event) = filterMotEvent(event)(_.message.length >= length)
  }

  case class AttributePresence(name: String) extends Filter {
    def filter(event: Event) = filterAttributes(event)(_.attributes.exists(_._1 == name))
  }

  case class AttributeValue(name: String, value: String) extends Filter {
    def filter(event: Event) = {
      filterAttributes(event) { as => 
        as.attributes.exists { case (n, v) => n == name && v.asString(UTF_8) == value } 
      }
    }
  }

  case class AttributeRegex(name: String, regex: String) extends Filter {
    val pattern = Pattern.compile(regex)
    def filter(event: Event) = {
      filterAttributes(event) { as => 
        as.attributes.exists { case (n, v) => n == name && pattern.matcher(v.asString(UTF_8)).matches() }
      }
    }
  }

  object All extends Filter {
    def filter(event: Event) = true
  }

}