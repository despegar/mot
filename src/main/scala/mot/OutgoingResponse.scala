package mot

case class OutgoingResponse(sequence: Int, responder: Responder, message: Message)