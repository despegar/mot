package mot.message

object MessageType extends Enumeration {
  
  val Hello = Value(0, "hello")
  val Heartbeat = Value(1, "heartbeat")
  val Message = Value(2, "message")
  val Response = Value(3, "response")
  
  def isValid(str: String) = values.exists(_.toString == str)
  
}