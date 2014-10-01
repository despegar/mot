package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._
import scala.collection.immutable
import java.util.concurrent.TimeUnit

class ServerConnections(context: Context) extends SimpleCommandHandler {

  val name = "server-connections"
  val helpLine = "Print information about server connections"

  def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]) = {
    import Tabler._
    import Alignment._
    Tabler.draw(
      Col[String]("SERVER", 20, Left),
      Col[String]("REMOTE-ADDR", 25, Left),
      Col[Long]("IDLE", 7, Right),
      Col[Int]("RCV-QUEUE", 9, Right),
      Col[String]("CLIENT", 17, Left),
      Col[Int]("CLI-MAX-LEN", 11, Right)) { printer =>
        for (server <- context.servers.values; conn <- server.connections.values) {
          printer(
            server.name,
            conn.from.toString,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - conn.lastReception),
            conn.sendingQueue.size,
            conn.clientName.getOrElse("-"),
            conn.responseMaxLength.getOrElse(-1))
        }
      }
  }

}