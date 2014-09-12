package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._
import scala.collection.immutable

class ServerConnections(context: Context) extends SimpleCommandHandler {

  val name = "server-connections"

  val helpLine = "Print information about server connections"

  def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]) = {
    import Tabler._
    Tabler.draw(
      Col[String]("SERVER", 17, Alignment.Left),
      Col[String]("CLIENT", 17, Alignment.Left),
      Col[String]("REMOTE-ADDR", 25, Alignment.Left),
      Col[Int]("SND-QUEUE", 9, Alignment.Right)) { printer =>
        for (server <- context.servers.values; conn <- server.connections.values) {
          printer(
            server.name,
            conn.clientName.getOrElse("-"),
            conn.from.toString,
            conn.sendingQueue.size)
        }
      }
  }

}