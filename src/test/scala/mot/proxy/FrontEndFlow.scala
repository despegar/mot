package mot.proxy

import mot.ServerConnectionHandler

case class FrontEndFlow(serverConnection: ServerConnectionHandler, frontendFlowId: Int) {
  
  var lastUse = System.nanoTime()
  
  def markUse() = {
    lastUse = System.nanoTime()
  }
  
}
