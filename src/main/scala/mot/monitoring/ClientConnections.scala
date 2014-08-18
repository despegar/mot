package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._

class ClientConnections extends SimpleCommandHandler {

  val name = "conn-client"

  val helpLine = "Print information about client connections"

  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]) = {
    import Tabler._
    Tabler.draw(
      Col[String]("CLIENT", 15, Alignment.Left),
      Col[String]("TARGET", 21, Alignment.Left),
      Col[Int]("QUEUE", 7, Alignment.Right),
      Col[Long]("UNRSP-SENT", 11, Alignment.Right),
      Col[Long]("RESP-SENT", 11, Alignment.Right),
      Col[Long]("RES-RCVD", 11, Alignment.Right),
      Col[Long]("TIMEOUTS", 11, Alignment.Right),
      Col[String]("LOCAL-ADDR", 26, Alignment.Left),
      Col[String]("REMOTE-ADDR", 26, Alignment.Left),
      Col[Int]("PENDING", 7, Alignment.Right),
      Col[String]("LAST ERROR", 50, Alignment.Left)) { printer =>
        for (client <- Context.clients.values; connector <- client.connectors.values) {
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
            connector.unrespondableSentCounter.get(),
            connector.respondableSentCounter.get(),
            connector.responsesReceivedCounter.get(),
            connector.timeoutsCounter.get(),
            local,
            remote,
            pending,
            lastError)
        }
      }
  }

}