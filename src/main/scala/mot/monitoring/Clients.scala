package mot.monitoring

import mot.util.Tabler
import mot.Context
import collection.JavaConversions._
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration
import scala.concurrent.duration.Duration.Infinite

class Clients(context: Context) extends SimpleCommandHandler {

  val name = "clients"
  val helpLine = "Print information about clients"

  def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]) = {
    import Tabler._
    import Alignment._
    Tabler.draw(
      Col[String]("NAME", 20, Left),
      Col[Int]("MAX-MSG-LEN", 11, Right),
      Col[Int]("MAX-SND-QUEUE", 13, Right),
      Col[Int]("READBUF-SIZE", 13, Right),
      Col[Int]("WRITEBUF-SIZE", 13, Right),
      Col[String]("TOLERANCE", 13, Left),
      Col[Int]("CONNECTORS", 12, Right)) { printer =>
        for (client <- context.clients.values) {
          printer(
            client.name,
            client.maxAcceptedLength,
            client.sendingQueueSize,
            client.readerBufferSize,
            client.writerBufferSize,
            durationToString(client.tolerance), 
            client.connectors.size)
        }
      }
  }
  
  private def durationToString(duration: Duration) = duration match {
    case fd: FiniteDuration => fd.toString
    case id: Infinite => "∞"
  }

}