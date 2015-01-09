package mot.protocol

object MessageTypes {
  
  val Hello: Byte = 0
  val Heartbeat: Byte = 1
  val Request: Byte = 2
  val Response: Byte = 3
  val Message: Byte = 4
  val FlowControl: Byte = 5
  val Bye: Byte = 6
  val Reset: Byte = 7
  
  val names = Map(
      "hello" -> Hello,
      "heartbeat" -> Heartbeat,
      "request" -> Request,
      "response" -> Response,
      "message" -> Message,
      "flow-control" -> FlowControl,
      "bye" -> Bye,
      "reset" -> Reset)
  
  val reverse = (names.map { case (name, id) => (id, name) }).toMap
  
  def isValid(messageType: String) = names.contains(messageType)

}
