package mot

case class Target(host: String, port: Int) {
  override def toString() = s"$host:$port"
}