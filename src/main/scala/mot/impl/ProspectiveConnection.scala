package mot.impl

import mot.Address

case class ProspectiveConnection(remoteAddress: Address, localName: String) extends Connection {
  def remoteName = ""
  def localAddress = null
}