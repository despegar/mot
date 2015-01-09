package mot.monitoring

import mot.Context
import scala.collection.immutable
import mot.util.Tabler
import mot.util.Tabler.Alignment
import collection.JavaConversions._
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

class ServerFlows(context: Context) extends SimpleCommandHandler {

  val name = "server-flows"
  val helpLine = "Print information about server flows"

  def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]): String = {
    import Tabler._
    import Alignment._
    Tabler.draw(
      Col[String]("SERVER", 20, Left),
      Col[String]("REMOTE-ADDR", 25, Left),
      Col[Int]("FLOW-ID", 7, Right),
      Col[Int]("SND-QUEUE", 9, Right),
      Col[Boolean]("OPEN", 7, Left),
      Col[String]("CLIENT", 17, Left),
      Col[Long]("IDLE", 9, Right)) { printer =>
        
        for {
          server <- context.servers.values
          conn <- server.connections.values
          flow <- conn.responseFlows.flows.values
        } {
          val now = System.nanoTime()
          val lastUse = flow.lastUse
          printer(
            server.name,
            conn.remoteAddress.toString,
            flow.id,
            flow.queue.size,
            flow.queue.isEnabled,
            conn.remoteNameOption.getOrElse("-"),
            Duration(now - lastUse, TimeUnit.NANOSECONDS).toMillis)
        }
      }
  }

}