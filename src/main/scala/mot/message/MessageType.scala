package mot.message

object MessageType extends Enumeration {
  val ClientHello = Value(0)
  val ServerHello = Value(1)
  val Heartbeat = Value(2)
  val Request = Value(3)
  val Response = Value(4)
  val Message = Value(5)
}