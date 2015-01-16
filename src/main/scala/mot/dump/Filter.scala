package mot.dump

import mot.Address
import mot.protocol.AttributesSupport
import mot.protocol.Frame

trait Filter {

  import Filters.Side

  def filter(event: Event): Boolean

  def filterAddress(event: Event, side: Side.Value)(filter: Address => Boolean) = {
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
      case Side.Any =>
        filter(event.conn.remoteAddress) || filter(event.conn.localAddress)
    }
  }

  def filterMotEvent(event: Event)(filter: MotEvent => Boolean) = {
    event match {
      case me: MotEvent => filter(me)
      case _ => false
    }
  }
  
  def filterAttributes(event: Event)(filter: AttributesSupport => Boolean) = {
    filterMotEvent(event) { me =>
      me.message match {
        case as: AttributesSupport => filter(as)
        case _ => false
      }
    }
  }

}



