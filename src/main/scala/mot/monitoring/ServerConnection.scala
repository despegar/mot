package mot.monitoring

import mot.util.LiveTabler
import mot.util.Tabler
import mot.Context
import mot.Target

class ServerConnection(context: Context) extends MultiCommandHandler {

  val subcommands = Seq(Live, Totals)

  val name = "server-connection"

  val helpLine = "Show server connection statistics."

  val interval = 1000

  class CommandException(error: String) extends Exception(error)

  object Live extends CommandHandler {
    val name = "live"
    def handle(processedCommands: Seq[String], commands: Seq[String], partWriter: String => Unit): String = {
      import LiveTabler._
      import Tabler._
      val connector = try {
        getConnector(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      LiveTabler.draw(
        partWriter,
        Col[Int]("SND-QUEUE", 9, Alignment.Right),
        Col[Long]("RCV-RESP", 10, Alignment.Right),
        Col[Long]("RCV-UNRESP", 10, Alignment.Right),
        Col[Long]("TOO-LATE", 10, Alignment.Right)
        ) { printer =>
          val respondable = Differ.fromVolatile(connector.receivedRespondable _)
          val unrespondable = Differ.fromVolatile(connector.receivedUnrespondable _)
          val tooLate = Differ.fromAtomic(connector.tooLateResponses)
          while (true) {
            Thread.sleep(interval)
            printer(
              connector.sendingQueue.size,
              respondable.diff(),
              unrespondable.diff(),
              tooLate.diff())
          }
        }
      throw new AssertionError
    }
  }

  object Totals extends SimpleCommandHandler {
    val name = "totals"
    def simpleHandle(processedCommands: Seq[String], commands: Seq[String]): String = {
      val connector = try {
        getConnector(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      "" +
        f"Sending queue size:           ${connector.sendingQueue.size}%11d\n" +
        f"Received respondable:         ${connector.receivedRespondable}%11d\n" +
        f"Received unrespondable:       ${connector.receivedUnrespondable}%11d\n" +
        f"Responses producted too late: ${connector.tooLateResponses.get}%11d\n"
    }
  }

  def getConnector(commands: Seq[String]) = {
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