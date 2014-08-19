package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._

class Servers extends SimpleCommandHandler {

  val name = "servers"

  val helpLine = "Print information about listening servers"

  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]) = {
    import Tabler._
    Tabler.draw(
      Col[String]("SERVER", 15, Alignment.Left),
      Col[String]("BIND-ADDR", 18, Alignment.Left),
      Col[Int]("BIND-PORT", 9, Alignment.Right),
      Col[Int]("CONNECTIONS", 11, Alignment.Right),
      Col[Int]("RCV-QUEUE", 9, Alignment.Right)) { printer =>
        for (server <- Context.servers.values) {
          printer(
            server.name,
            server.bindAddress.toString,
            server.bindPort,
            server.connectors.size,
            server.receivingQueue.size)
        }
      }
  }

}