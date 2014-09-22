package mot.message

object MessageType extends Enumeration {
  val Hello = Value(0)
  val Heartbeat = Value(2)
  val Response = Value(4)
  val Message = Value(5)
}