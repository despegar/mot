package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._
import scala.collection.immutable

class Servers(context: Context) extends SimpleCommandHandler {

  val name = "servers"
  val helpLine = "Print information about listening servers"

  def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]) = {
    import Tabler._
    import Alignment._
    Tabler.draw(
      Col[String]("NAME", 20, Left),
      Col[String]("BIND-ADDR", 18, Left),
      Col[Int]("BIND-PORT", 9, Right),
      Col[Int]("MAX-MSG-LEN", 11, Right),
      Col[Int]("MAX-RCV-QUEUE", 13, Right),
      Col[Int]("RCV-QUEUE", 13, Right),
      Col[Int]("MAX-SND-QUEUE", 13, Right),
      Col[Int]("READBUF-SIZE", 12, Right),
      Col[Int]("WRITEBUF-SIZE", 13, Right),
      Col[Int]("CONNECTIONS", 11, Right)) { printer =>
        for (server <- context.servers.values) {
          printer(
            server.name,
            server.bindAddress.getHostAddress,
            server.bindPort,
            server.maxAcceptedLength,
            server.receivingQueueSize,
            server.receivingQueue.size,
            server.sendingQueueSize,
            server.readerBufferSize,
            server.writerBufferSize,
            server.connections.size)
        }
      }
  }

}