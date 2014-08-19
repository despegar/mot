package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._

class ServerConnections extends SimpleCommandHandler {

  val name = "server-conn"

  val helpLine = "Print information about server connections"

  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]) = {
    import Tabler._
    Tabler.draw(
      Col[String]("SERVER", 15, Alignment.Left),
      Col[String]("CLIENT", 15, Alignment.Left),
      Col[String]("REMOTE-ADDR", 25, Alignment.Left),
      Col[Int]("SND-QUEUE", 9, Alignment.Right)) { printer =>
        for (server <- Context.servers.values; conn <- server.connectors.values) {
          printer(
            server.name,
            conn.clientName,
            conn.from.toString,
            conn.sendingQueue.size)
        }
      }
  }

}