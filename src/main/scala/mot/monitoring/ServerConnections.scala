package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._
import java.util.concurrent.TimeUnit

class ServerConnections(context: Context) extends SimpleCommandHandler {

  val name = "server-connections"
  val helpLine = "Print information about server connections"

  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]) = {
    import Tabler._
    import Alignment._
    Tabler.draw(
      Col[String]("SERVER", 20, Left),
      Col[String]("REMOTE-ADDR", 25, Left),
      Col[Long]("IDLE", 7, Right),
      Col[String]("CLIENT", 17, Left),
      Col[Int]("CLI-MAX-LEN", 11, Right)) { printer =>
        for (server <- context.servers.values; conn <- server.connections.values) {
          printer(
            server.name,
            conn.remoteAddress.toString,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - conn.lastRead.get),
            conn.remoteNameOption.getOrElse("-"),
            conn.remoteMaxLength.getOrElse(-1))
        }
      }
  }

}