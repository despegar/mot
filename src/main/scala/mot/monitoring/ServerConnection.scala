package mot.monitoring

import mot.util.LiveTabler
import mot.util.Tabler
import mot.Context
import mot.Address
import mot.util.Util.CeilingDivider
import mot.util.Util.atomicLong2Getter
import mot.util.Differ

class ServerConnection(context: Context) extends MultiCommandHandler {

  val subcommands = Seq(Live, Totals)
  val name = "server-connection"
  val helpLine = "Show server connection statistics."

  class CommandException(error: String) extends Exception(error)

  object Live extends CommandHandler {
    val name = "live"
    def handle(processedCommands: Seq[String], commands: Seq[String], partWriter: String => Unit): String = {
      import LiveTabler._
      import Tabler._
      import Alignment._
      val connection = try {
        getConnection(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      LiveTabler.draw(
        partWriter,
        Col[Long]("REQ-RCVD", 9, Right),
        Col[Long]("MSG-RCVD", 9, Right),
        Col[Long]("RES-SENT", 9, Right),
        Col[Long]("TOO-LARGE", 9, Right),
        Col[Long]("KB-READ", 9, Right),
        Col[Long]("KB-WRITTEN", 10, Right),
        Col[Long]("SOCK-READS", 10, Right),
        Col[Long]("READ-FULL", 10, Right),
        Col[Long]("SOCK-WRITES", 11, Right),
        Col[Long]("WRITE-FULL", 10, Right),
        Col[Long]("DIR-WRITES", 10, Right)) { printer =>
          val respondable = new Differ(connection.receivedRespondable _)
          val unrespondable = new Differ(connection.receivedUnrespondable _)
          val sent = new Differ(connection.sentResponses _)
          val tooLarge = new Differ(connection.tooLargeResponses)
          val bytesRead = new Differ(connection.readBuffer.bytesCount)
          val bytesWriten = new Differ(connection.writeBuffer.bytesCount)
          val socketReads = new Differ(connection.readBuffer.readCount)
          val socketReadFull = new Differ(connection.readBuffer.fullReadCount)
          val socketWrites = new Differ(connection.writeBuffer.writeCount)
          val socketWriteFull = new Differ(connection.writeBuffer.fullWriteCount)
          val directWrites = new Differ(connection.writeBuffer.directWriteCount)
          while (true) {
            Thread.sleep(Commands.liveInterval)
            printer(
              respondable.diff(),
              unrespondable.diff(),
              sent.diff(),
              tooLarge.diff(),
              bytesRead.diff() /^ 1024,
              bytesWriten.diff() /^ 1024,
              socketReads.diff(),
              socketReadFull.diff(),
              socketWrites.diff(),
              socketWriteFull.diff(),
              directWrites.diff())
          }
        }
      throw new AssertionError
    }
  }

  object Totals extends SimpleCommandHandler {
    val name = "totals"
    def simpleHandle(processedCommands: Seq[String], commands: Seq[String]): String = {
      val connection = try {
        getConnection(commands)
      } catch {
        case e: CommandException => return e.getMessage
      }
      "" +
        f"Total messages received:             ${connection.receivedRespondable}%11d\n" +
        f"Total requests received:             ${connection.receivedUnrespondable}%11d\n" +
        f"Total responses sent:                ${connection.sentResponses}%11d\n" +
        f"Total responses that were too large: ${connection.tooLargeResponses.get}%11d\n" +
        f"Total bytes read:                    ${connection.readBuffer.bytesCount}%11d\n" +
        f"Total bytes written:                 ${connection.writeBuffer.bytesCount}%11d\n" +
        f"Total socket reads:                  ${connection.readBuffer.readCount}%11d\n" +
        f"Total socket reads (full buffer):    ${connection.readBuffer.fullReadCount}%11d\n"
        f"Total socket writes:                 ${connection.writeBuffer.writeCount}%11d\n" +
        f"Total socket writes (full buffer):   ${connection.writeBuffer.fullWriteCount}%11d\n"
        f"Total direct writes:                 ${connection.writeBuffer.directWriteCount}%11d\n"
    }
  }

  def getConnection(commands: Seq[String]) = {
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