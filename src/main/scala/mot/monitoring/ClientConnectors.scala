package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._
import scala.collection.immutable

class ClientConnectors(context: Context) extends SimpleCommandHandler {

  val name = "client-connectors"

  val helpLine = "Print information about client connectors"

  def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]) = {
    import Tabler._
    Tabler.draw(
      Col[String]("CLIENT", 17, Alignment.Left),
      Col[String]("TARGET", 25, Alignment.Left),
      Col[Int]("SND-QUEUE", 9, Alignment.Right),
      Col[String]("LOCAL-ADDR", 25, Alignment.Left),
      Col[String]("REMOTE-ADDR", 25, Alignment.Left),
      Col[String]("SERVER", 17, Alignment.Left),
      Col[Int]("MAX-LEN", 9, Alignment.Right),
      Col[Int]("PENDING", 7, Alignment.Right),
      Col[String]("LAST ERROR", 20, Alignment.Left)) { printer =>
        for (client <- context.clients.values; connector <- client.connectors.values) {
          val lastError = connector.lastConnectingError.map(_.getMessage).getOrElse("-")
          val (local, remote, serverName, maxLength, pending) = connector.currentConnection match {
            case Some(conn) =>
              (
                conn.socket.getLocalAddress.getHostAddress + ":" + conn.socket.getLocalPort,
                conn.socket.getInetAddress.getHostAddress + ":" + conn.socket.getPort,
                conn.serverName,
                conn.maxLength,
                conn.pendingResponses.size)
            case None =>
              ("-", "-", None, None, 0)
          }
          printer(
            client.name,
            connector.target.toString,
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
