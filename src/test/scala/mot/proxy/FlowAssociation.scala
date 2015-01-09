package mot.proxy

import mot.impl.ServerConnectionHandler
import mot.ClientFlow

case class FlowAssociation(serverConnecton: ServerConnectionHandler, frontendFlowId: Int, backendFlow: ClientFlow)

