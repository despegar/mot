package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._
import scala.collection.immutable
import java.util.concurrent.TimeUnit

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
          val lastError = connector.lastConnectingError.map(_.getMessage).getOrElse("-")
          val (local, remote, serverName, maxLength, pending) = connector.currentConnection match {
            case Some(conn) => (
                conn.socket.getLocalAddress.getHostAddress + ":" + conn.socket.getLocalPort,
                conn.socket.getInetAddress.getHostAddress + ":" + conn.socket.getPort,
                conn.serverName,
                conn.requestMaxLength,
                conn.pendingResponses.size)
            case None =>
              ("-", "-", None, None, 0)
          }
          val now = System.nanoTime()
          val idle = TimeUnit.NANOSECONDS.toSeconds(now - connector.lastUse)
          printer(
            client.name,
            connector.target.toString,
            idle,
            connector.sendingQueue.size,
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
