package mot.dump

import mot.Address

trait Filter {

  import Filters.Side
    
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
      case Side.Any =>
        filter(event.conn.remoteAddress) || filter(event.conn.localAddress)
    }
  }

}



