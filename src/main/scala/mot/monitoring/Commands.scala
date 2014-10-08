package mot.monitoring

import java.net.ServerSocket
import java.net.InetSocketAddress
import mot.Util.FunctionToRunnable
import scala.io.Source
import scala.util.control.NonFatal
import java.net.SocketException
import java.io.PrintStream
import java.net.Socket
import mot.Context
import scala.collection.immutable
import com.typesafe.scalalogging.slf4j.StrictLogging
import java.nio.charset.StandardCharsets

class Commands(context: Context, monitoringPort: Int) extends StrictLogging with MultiCommandHandler {

  val serverSocket = new ServerSocket()

  def start() = {
    serverSocket.bind(new InetSocketAddress(monitoringPort))
    new Thread(doIt _, "mot-commands-acceptor").start()
  }

  def doIt() {
    while (true) {
      val socket = serverSocket.accept()
      new Thread(() => processClient(socket), "mot-command-handler-for-" + socket.getRemoteSocketAddress).start()
    }
  }

  def processClient(socket: Socket) = {
    val is = socket.getInputStream
    val os = socket.getOutputStream
    try {
      val req = Source.fromInputStream(is).mkString("")
      val reqParts = if (req.isEmpty) immutable.Seq() else req.split(" ").to[immutable.Seq]
      def writer(part: String): Unit = {
        os.write(part.getBytes(StandardCharsets.UTF_8))
        os.write('\n')
      }
      val res = handle(immutable.Seq(name), reqParts, writer)
      writer(res)
    } catch {
      case e: SocketException =>
        logger.info(s"Client ${socket.getRemoteSocketAddress} gone (${e.getMessage})")
      case NonFatal(e) =>
        logger.error("Error processing message", e)
        try {
          val ps = new PrintStream(os)
          e.printStackTrace(ps)
          ps.flush()
        } catch {
          case NonFatal(e) => logger.error("Could not send message in catch block", e)
        }
    } finally {
      socket.close()
    }
  }

  val helpLine = "Mot command-line interface."

  val name = "cnd"

  val subcommands = immutable.Seq(
    new Clients(context),
    new ClientConnectors(context),
    new ClientConnector(context),
    new Servers(context),
    new ServerConnections(context),
    new ServerConnection(context))

}

object Commands {
  val liveInterval = 1000
}