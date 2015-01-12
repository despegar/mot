package mot.proxy

import mot.ServerConnectionHandler
import mot.ClientFlow

case class FlowAssociation(serverConnection: ServerConnectionHandler, frontendFlowId: Int)

