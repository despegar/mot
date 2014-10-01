package mot.monitoring

import mot.util.LiveTabler
import mot.util.Tabler
import mot.Context
import mot.util.Util.CeilingDivider
import scala.collection.immutable
import mot.Address
import mot.util.Util.atomicLong2Getter

class ClientConnector(context: Context) extends MultiCommandHandler {

  val subcommands = immutable.Seq(Live, Totals)
  val name = "client-connector"
  val helpLine = "Show client connector statistics."

  class CommandException(error: String) extends Exception(error)

  object Live extends CommandHandler {
    val name = "live"
    def handle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String], partWriter: String => Unit): String = {
      import LiveTabler._
      import Tabler._
      import Alignment._
      val connector = try {
        getConnector(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      val connection = connector.currentConnection.getOrElse(return "Not currently connected")
      LiveTabler.draw(
        partWriter,
        Col[Int]("SND-QUEUE", 9, Right),
        Col[Int]("PENDING", 8, Right),
        Col[Long]("MSG-ENQ", 9, Right),
        Col[Long]("MSG-SENT", 9, Right),
        Col[Long]("REQ-ENQ", 9, Right),
        Col[Long]("REQ-SENT", 9, Right),
        Col[Long]("RES-EXPIR", 9, Right),
        Col[Long]("RES-RECV", 9, Right),
        Col[Long]("TIMEOUT", 9, Right),
        Col[Long]("REQ-TOO-BIG", 11, Right),
        Col[Long]("KB-WRITTEN", 10, Right),
        Col[Long]("KB-READ", 10, Right),
        Col[Long]("SOCK-WRITES", 11, Right),
        Col[Long]("WRITE-FULL", 10, Right),
        Col[Long]("SOCK-READS", 10, Right),
        Col[Long]("READ-FULL", 9, Right)) { printer =>
          val messageEnqueued = new Differ(connector.unrespondableEnqueued)
          val messageSent = new Differ(connector.unrespondableSentCounter _)
          val requestEnqueued = new Differ(connector.respondableEnqueued)
          val requestSent = new Differ(connector.respondableSentCounter _)
          val requestExpiredInQueue = new Differ(connector.expiredInQueue _)
          val responseReceived = new Differ(connector.responsesReceivedCounter _)
          val timeouts = new Differ(connector.timeoutsCounter _)
          val sendTooLarge = new Differ(connector.triedToSendTooLargeMessage _)
          val bytesWriten = new Differ(connection.writeBuffer.bytesCount)
          val bytesRead = new Differ(connection.readBuffer.bytesCount)
          val socketWrites = new Differ(connection.writeBuffer.writeCount)
          val socketWritesFull = new Differ(connection.writeBuffer.fullWriteCount)
          val socketReads = new Differ(connection.readBuffer.readCount)
          val socketReadFull = new Differ(connection.readBuffer.fullReadCount)
          while (true) {
            if (connection.isClosed)
              return "Disconnected"
            Thread.sleep(Commands.liveInterval)
            printer(
              connector.sendingQueue.size,
              connection.pendingResponses.size,
              messageEnqueued.diff(),
              messageSent.diff(),
              requestEnqueued.diff(),
              requestSent.diff(),
              requestExpiredInQueue.diff(),
              responseReceived.diff(),
              timeouts.diff(),
              sendTooLarge.diff(),
              bytesWriten.diff() /^ 1024,
              bytesRead.diff() /^ 1024,
              socketWrites.diff(),
              socketWritesFull.diff(),
              socketReads.diff(),
              socketReadFull.diff())
          }
        }
      throw new AssertionError
    }
  }

  object Totals extends SimpleCommandHandler {
    val name = "totals"
    def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]): String = {
      val connector = try {
        getConnector(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      val connection = connector.currentConnection.getOrElse(return "Not currently connected")
      "" +
        f"Sending queue size:                ${connector.sendingQueue.size}%11d\n" +
        f"Pending responses:                 ${connection.pendingResponses.size}%11d\n" +
        f"Total messages enqueued:           ${connector.unrespondableEnqueued.get}%11d\n" +
        f"Total messages sent:               ${connector.unrespondableSentCounter}%11d\n" +
        f"Total requests enqueued:           ${connector.respondableEnqueued.get}%11d\n" +
        f"Total requests sent:               ${connector.respondableSentCounter}%11d\n" +
        f"Total requests expired             ${connector.expiredInQueue}%11d\n" +
        f"Total responses received:          ${connector.responsesReceivedCounter}%11d\n" +
        f"Total timed out requests:          ${connector.timeoutsCounter}%11d\n"
        f"Total requests too large:          ${connector.triedToSendTooLargeMessage}%11d\n"
        f"Total bytes written:               ${connection.writeBuffer.bytesCount}%11d\n" +
        f"Total bytes read:                  ${connection.readBuffer.bytesCount}%11d\n" +
        f"Total socket reads:                ${connection.readBuffer.readCount}%11d\n" +
        f"Total socket reads (full buffer):  ${connection.readBuffer.fullReadCount}%11d\n"
        f"Total socket writes:               ${connection.writeBuffer.writeCount}%11d\n" +
        f"Total socket writes (full buffer): ${connection.writeBuffer.fullWriteCount}%11d\n"
    }
  }

  def getConnector(commands: immutable.Seq[String]) = {
    if (commands.size < 2)
      throw new CommandException("Must specify client and target")
    val clientName +: targetName +: rest = commands
    val client = Option(context.clients.get(clientName)).getOrElse(throw new CommandException("Unknown client: " + clientName))
    val target = Address.fromString(targetName)
    Option(client.connectors.get(target)).getOrElse(throw new CommandException("Unknown target: " + targetName))
  }

}