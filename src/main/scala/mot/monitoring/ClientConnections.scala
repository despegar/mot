package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._

class ClientConnections extends SimpleCommandHandler {

  val name = "conn-client"

  val helpLine = "Print information about connections"

  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]) = {
    import Tabler._
    Tabler.draw(
      Col[String]("CLIENT", 20, Alignment.Left),
      Col[String]("STATUS", 10, Alignment.Left),
      Col[String]("LOCAL-ADDR", 30, Alignment.Left),
      Col[String]("REMOTE-ADDR", 30, Alignment.Left),
      Col[String]("LAST ERROR", 50, Alignment.Left)) { printer =>
        for (client <- Context.clients.values; connector <- client.connectors.values) {
          val lastError = connector.lastConnectingError.map(_.getMessage).getOrElse("-")
          connector.currentConnection match {
            case Some(connection) =>
              printer(
                client.name,
                "CONNECTED",
                connection.socket.getLocalSocketAddress.toString,
                connection.socket.getRemoteSocketAddress.toString,
                lastError)
            case None =>
              printer(
                client.name,
                "CONNECTING",
                "-",
                "-", 
                lastError)
          }
        }
      }
  }

}