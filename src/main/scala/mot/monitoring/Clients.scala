package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._

class Clients(context: Context) extends SimpleCommandHandler {

  val name = "clients"

  val helpLine = "Print information about clients"

  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]) = {
    import Tabler._
    Tabler.draw(
      Col[String]("NAME", 15, Alignment.Left),
      Col[Int]("MAX-LENGTH", 10, Alignment.Right),
      Col[Int]("QUEUE-MAXSIZE", 13, Alignment.Right),
      Col[Int]("RBUF-SIZE", 9, Alignment.Right),
      Col[Int]("WBUF-SIZE", 9, Alignment.Right),
      Col[Int]("CONN-TO", 9, Alignment.Right),
      Col[Boolean]("PESSIMISTIC", 11, Alignment.Left),
      Col[Int]("CONNECTORS", 12, Alignment.Right)) { printer =>
        for (client <- context.clients.values) {
          printer(
            client.name,
            client.responseMaxLength,
            client.queueSize,
            client.readerBufferSize,
            client.writerBufferSize,
            client.connectTimeout,
            client.pessimistic, 
            client.connectors.size)
        }
      }
  }

}