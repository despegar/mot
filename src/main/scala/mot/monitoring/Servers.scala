package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._

class Servers(context: Context) extends SimpleCommandHandler {

  val name = "servers"

  val helpLine = "Print information about listening servers"

  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]) = {
    import Tabler._
    Tabler.draw(
      Col[String]("NAME", 15, Alignment.Left),
      Col[String]("BIND-ADDR", 18, Alignment.Left),
      Col[Int]("BIND-PORT", 9, Alignment.Right),
      Col[Int]("MAX-MSG-LEN", 11, Alignment.Right),
      Col[Int]("RCV-QUEUE-MAX", 13, Alignment.Right),
      Col[Int]("RCV-QUEUE", 13, Alignment.Right),
      Col[Int]("SND-QUEUE-MAX", 13, Alignment.Right),
      Col[Int]("READBUF-SIZE", 12, Alignment.Right),
      Col[Int]("WRITEBUF-SIZE", 13, Alignment.Right),
      Col[Int]("CONNECTORS", 10, Alignment.Right)) { printer =>
        for (server <- context.servers.values) {
          printer(
            server.name,
            server.bindAddress.toString,
            server.bindPort,
            server.requestMaxLength,
            server.receivingQueueSize,
            server.receivingQueue.size,
            server.sendingQueueSize,
            server.readerBufferSize,
            server.writerBufferSize,
            server.connectors.size)
        }
      }
  }

}