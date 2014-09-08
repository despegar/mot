package mot.monitoring

import mot.util.LiveTabler
import mot.util.Tabler
import mot.Context
import mot.Target
import mot.util.Util.CeilingDivider
import scala.collection.immutable

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
        Col[Long]("RCV-RESP", 11, Alignment.Right),
        Col[Long]("RCV-UNRESP", 11, Alignment.Right),
        Col[Long]("SENT-RESP", 11, Alignment.Right),
        Col[Long]("TOO-LATE", 11, Alignment.Right),
        Col[Long]("KB-READ", 11, Alignment.Right),
        Col[Long]("KB-WRITTEN", 11, Alignment.Right),
        Col[Long]("BUF-FILLINGS", 12, Alignment.Right),
        Col[Long]("BUF-FULL", 11, Alignment.Right)) { printer =>
          val respondable = Differ.fromVolatile(connection.receivedRespondable _)
          val unrespondable = Differ.fromVolatile(connection.receivedUnrespondable _)
          val sent = Differ.fromVolatile(connection.sentResponses _)
          val tooLate = Differ.fromAtomic(connection.tooLateResponses)
          val bytesRead = Differ.fromVolatile(connection.readBuffer.bytesCount)
          val bytesWriten = Differ.fromVolatile(connection.writeBuffer.bytesCount)
          val fillings = Differ.fromVolatile(connection.readBuffer.readCount)
          val fillingsFull = Differ.fromVolatile(connection.readBuffer.bufferFullCount)
          while (true) {
            Thread.sleep(interval)
            printer(
              connection.sendingQueue.size,
              respondable.diff(),
              unrespondable.diff(),
              sent.diff(),
              tooLate.diff(),
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
    val origin = Target.fromString(originName)
    Option(server.connections.get(origin)).getOrElse(throw new CommandException("Unknown origin: " + originName))
  }

}