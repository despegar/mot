package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._
import scala.collection.immutable
import java.util.concurrent.TimeUnit
import scala.util.Success
import scala.util.Failure

class ClientConnectors(context: Context) extends SimpleCommandHandler {

  val name = "client-connectors"
  val helpLine = "Print information about client connectors"

  def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]) = {
    import Tabler._
    import Alignment._
    Tabler.draw(
      Col[String]("CLIENT", 20, Left),
      Col[String]("TARGET", 25, Left),
      Col[Long]("IDLE", 7, Right),
      Col[Int]("SND-QUEUE", 9, Right),
      Col[String]("LOCAL-ADDR", 25, Left),
      Col[String]("REMOTE-ADDR", 25, Left),
      Col[String]("SERVER", 17, Left),
      Col[Int]("SRV-MAX-LEN", 11, Right),
      Col[Int]("PENDING", 7, Right),
      Col[String]("LAST ERROR", 20, Left)) { printer =>
        for (client <- context.clients.values; connector <- client.connectors.values) {
          val (local, remote, serverName, maxLength, pending, lastError) = connector.currentConnection match {
            case Success(conn) => (
                conn.socket.impl.getLocalAddress.getHostAddress + ":" + conn.socket.impl.getLocalPort,
                conn.socket.impl.getInetAddress.getHostAddress + ":" + conn.socket.impl.getPort,
                conn.remoteNameOption,
                conn.remoteMaxLength,
                connector.pendingResponses.size,
                "-")
            case Failure(ex) =>
              ("-", "-", None, None, 0, ex.getMessage)
          }
          val now = System.nanoTime()
          val idle = TimeUnit.NANOSECONDS.toSeconds(now - connector.lastUse)
          printer(
            client.name,
            connector.target.toString,
            idle,
            connector.messagesQueue.size,
            local,
            remote,
            serverName.getOrElse("-"),
            maxLength.getOrElse(-1),
            pending,
            lastError)
        }
      }
  }

}
