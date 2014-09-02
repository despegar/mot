package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._

class Connectors(context: Context) extends SimpleCommandHandler {

  val name = "connectors"

  val helpLine = "Print information about client connectors"

  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]) = {
    import Tabler._
    Tabler.draw(
      Col[String]("CLIENT", 13, Alignment.Left),
      Col[String]("TARGET", 21, Alignment.Left),
      Col[Int]("SND-QUEUE", 9, Alignment.Right),
      Col[String]("LOCAL-ADDR", 30, Alignment.Left),
      Col[String]("REMOTE-ADDR", 30, Alignment.Left),
      Col[Int]("PENDING", 7, Alignment.Right),
      Col[String]("LAST ERROR", 50, Alignment.Left)) { printer =>
        for (client <- context.clients.values; connector <- client.connectors.values) {
          val lastError = connector.lastConnectingError.map(_.getMessage).getOrElse("-")
          val (local, remote, pending) = connector.currentConnection match {
            case Some(conn) =>
              val s = conn.socket
              (s.getLocalSocketAddress.toString, s.getRemoteSocketAddress.toString, conn.pendingPromises.size)
            case None =>
              ("-", "-", 0)
          }
          printer(
            client.name,
            connector.target.toString,
            connector.sendingQueue.size,
            local,
            remote,
            pending,
            lastError)
        }
      }
  }

}