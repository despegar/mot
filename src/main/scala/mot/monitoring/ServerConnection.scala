package mot.monitoring

import mot.util.LiveTabler
import mot.util.Tabler
import mot.Context
import mot.Address
import mot.util.Util.CeilingDivider
import scala.collection.immutable
import mot.util.Util.atomicLong2Getter

class ServerConnection(context: Context) extends MultiCommandHandler {

  val subcommands = immutable.Seq(Live, Totals)

  val name = "server-connection"

  val helpLine = "Show server connection statistics."

  val interval = 1000

  class CommandException(error: String) extends Exception(error)

  object Live extends CommandHandler {
    val name = "live"
    def handle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String], partWriter: String => Unit): String = {
      import LiveTabler._
      import Tabler._
      val connection = try {
        getConnection(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      LiveTabler.draw(
        partWriter,
        Col[Int]("SND-QUEUE", 9, Alignment.Right),
        Col[Long]("REQ-RCVD", 11, Alignment.Right),
        Col[Long]("MSG-RCVD", 11, Alignment.Right),
        Col[Long]("RES-SENT", 11, Alignment.Right),
        Col[Long]("TOO-LATE", 11, Alignment.Right),
        Col[Long]("TOO-LARGE", 11, Alignment.Right),
        Col[Long]("KB-READ", 11, Alignment.Right),
        Col[Long]("KB-WRITTEN", 11, Alignment.Right),
        Col[Long]("BUF-FILLINGS", 12, Alignment.Right),
        Col[Long]("BUF-FULL", 11, Alignment.Right)) { printer =>
          val respondable = new Differ(connection.receivedRespondable _)
          val unrespondable = new Differ(connection.receivedUnrespondable _)
          val sent = new Differ(connection.sentResponses _)
          val tooLate = new Differ(connection.tooLateResponses)
          val tooLarge = new Differ(connection.tooLargeResponses)
          val bytesRead = new Differ(connection.readBuffer.bytesCount)
          val bytesWriten = new Differ(connection.writeBuffer.bytesCount)
          val fillings = new Differ(connection.readBuffer.readCount)
          val fillingsFull = new Differ(connection.readBuffer.bufferFullCount)
          while (true) {
            Thread.sleep(interval)
            printer(
              connection.sendingQueue.size,
              respondable.diff(),
              unrespondable.diff(),
              sent.diff(),
              tooLate.diff(),
              tooLarge.diff(),
              bytesRead.diff() /^ 1024,
              bytesWriten.diff() /^ 1024,
              fillings.diff(),
              fillingsFull.diff())
          }
        }
      throw new AssertionError
    }
  }

  object Totals extends SimpleCommandHandler {
    val name = "totals"
    def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]): String = {
      val connection = try {
        getConnection(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      "" +
        f"Sending queue size:           ${connection.sendingQueue.size}%11d\n" +
        f"Received respondable:         ${connection.receivedRespondable}%11d\n" +
        f"Received unrespondable:       ${connection.receivedUnrespondable}%11d\n" +
        f"Sent responses:               ${connection.sentResponses}%11d\n" +
        f"Responses producted too late: ${connection.tooLateResponses.get}%11d\n" +
        f"Responses too large:          ${connection.tooLargeResponses.get}%11d\n" +
        f"Total bytes read:             ${connection.readBuffer.bytesCount}%11d\n" +
        f"Total bytes written:          ${connection.writeBuffer.bytesCount}%11d\n" +
        f"Total buffer fillings:        ${connection.readBuffer.readCount}%11d\n" +
        f"Total full buffer fillings:   ${connection.readBuffer.bufferFullCount}%11d\n"
    }
  }

  def getConnection(commands: immutable.Seq[String]) = {
    if (commands.size < 2)
      throw new CommandException("Must specify server and origin")
    val serverName +: originName +: rest = commands
    val server = Option(context.servers.get(serverName)).getOrElse {
      throw new CommandException("Unknown server: " + serverName)
    }
    val origin = Address.fromString(originName)
    Option(server.connections.get(origin)).getOrElse(throw new CommandException("Unknown origin: " + originName))
  }

}