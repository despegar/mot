package mot.impl

import mot.Message
import mot.Responder

trait OutgoingEvent

case class OutgoingMessage(message: Message, pendingResponse: Option[PendingResponse]) extends OutgoingEvent
case class FlowNotification(flowId: Int, status: Boolean) extends OutgoingEvent
case class OutgoingResponse(requestId: Int, message: Message) extends OutgoingEvent