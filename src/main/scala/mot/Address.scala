package mot

import scala.collection.immutable

case class Address(host: String, port: Int) {
  override def toString() = s"$host:$port"
}

object Address {
  def fromString(str: String): Address = {
    val parts = str.split(":").to[immutable.Seq]
    if (parts.size != 2)
      throw new IllegalArgumentException("Cannot parse target: " + str)
    val immutable.Seq(host, portStr) = parts
    val port = try {
      portStr.toInt
    } catch {
      case e: NumberFormatException => throw new IllegalArgumentException("Port is not a number: " + portStr)
    }
    Address(host, port)
  }
}