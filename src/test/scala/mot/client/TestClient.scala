package mot.client

import mot.Address

trait TestClient {

  def parseArgs(args: Array[String]) = {
    val respondible = args(0).toBoolean
    val monitoringPort = args(1).toInt
    val dumpPort = args(2).toInt
    val target = Address.fromString(args(3))
    val proxyOpt = if (args.size >= 5)
      Some(Address.fromString(args(4)))
    else 
      None
    (respondible, monitoringPort, dumpPort, target, proxyOpt)
  }
  
}