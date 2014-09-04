package mot

case class Target(host: String, port: Int) {
  override def toString() = s"$host:$port"
}

object Target {
  def fromString(str: String) = {
    val parts = str.split(":").toSeq
    if (parts.size != 2)
      throw new IllegalArgumentException("Cannot parse target: " + str)
    val Seq(host, portStr) = parts
    val port = try {
      portStr.toInt
    } catch {
      case e: NumberFormatException => throw new IllegalArgumentException("Port is not a number: " + portStr)
    }
    Target(host, port)
  }
}