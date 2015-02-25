package mot

import java.net.InetSocketAddress

/**
 * Destination for messages.
 * 
 * @param host 
 *   Host to connect to. Can be an IP address or a domain name. In the latter case, all records are tried sequentially
 *   in case of error.
 * @param port 
 *   TCP port to connect to.
 */
case class Address(host: String, port: Int) {
  override def toString() = s"$host:$port"
}

object Address {
  
  def fromString(str: String): Address = {
    val parts = str.split(":", 2)
    if (parts.length != 2)
      throw new IllegalArgumentException("Cannot parse target: " + str)
    val (host, portStr) = (parts(0), parts(1))
    val port = try {
      portStr.toInt
    } catch {
      case e: NumberFormatException => throw new IllegalArgumentException("Port is not a number: " + portStr)
    }
    Address(host, port)
  }
  
  def fromInetSocketAddress(isa: InetSocketAddress): Address = Address(isa.getAddress.getHostAddress, isa.getPort)
  
}