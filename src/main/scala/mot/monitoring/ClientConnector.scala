package mot.monitoring

import mot.util.LiveTabler
import mot.util.Tabler
import mot.Context
import mot.Target

class ClientConnector(context: Context) extends MultiCommandHandler {

  val subcommands = Seq(Live, Totals)

  val name = "client-connector"

  val helpLine = "Show connector statistics."

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
        Col[Int]("PENDING", 9, Alignment.Right),
        Col[Long]("SENT-UNRSP", 11, Alignment.Right),
        Col[Long]("SENT-RESP", 11, Alignment.Right),
        Col[Long]("RESP-RCVD", 11, Alignment.Right),
        Col[Long]("TIMEOUTS", 11, Alignment.Right)) { printer =>
          val unrespondableSent = new Differ(connector.unrespondableSentCounter)
          val respondableSent = new Differ(connector.respondableSentCounter)
          val responsesReceived = new Differ(connector.responsesReceivedCounter)
          val timeouts = new Differ(connector.timeoutsCounter)
          while (true) {
            Thread.sleep(interval)
            printer(
              connector.sendingQueue.size,
              connector.currentConnection.map(_.pendingPromises.size).getOrElse(0),
              unrespondableSent.diff(),
              respondableSent.diff(),
              responsesReceived.diff(),
              timeouts.diff())
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
        f"Sending queue size:                ${connector.sendingQueue.size}%11d\n" +
        f"Pending responses:                 ${connector.currentConnection.map(_.pendingPromises.size).getOrElse(0)}%11d\n" +
        f"Total unrespondable messages sent: ${connector.unrespondableSentCounter.get}%11d\n" +
        f"Total respondable messages sent:   ${connector.respondableSentCounter.get}%11d\n" +
        f"Total responses received:          ${connector.responsesReceivedCounter.get}%11d\n" +
        f"Total timed out messages:          ${connector.timeoutsCounter.get}%11d\n"
    }
  }

  def getConnector(commands: Seq[String]) = {
    if (commands.size < 2)
      throw new CommandException("Must specify client and target")
    val clientName +: targetName +: rest = commands
    val client = Option(context.clients.get(clientName)).getOrElse {
      throw new CommandException("Unknown client: " + clientName)
    }
    val target = Target.fromString(targetName)
    Option(client.connectors.get(target)).getOrElse(throw new CommandException("Unknown target: " + targetName))
  }

}