package mot.monitoring

import java.net.ServerSocket
import java.net.InetSocketAddress
import mot.util.Util.FunctionToRunnable
import scala.io.Source
import scala.util.control.NonFatal
import java.net.SocketException
import java.io.PrintStream
import java.net.Socket
import mot.Context
import com.typesafe.scalalogging.slf4j.StrictLogging
import java.nio.charset.StandardCharsets
import mot.util.Util
import java.io.IOException

class Commands(context: Context, monitoringPort: Int) extends StrictLogging with MultiCommandHandler {

  val serverSocket = new ServerSocket()

  def start() = {
    // TODO: Make binding configurable
    serverSocket.bind(new InetSocketAddress("127.0.0.1", monitoringPort))
    acceptorThread.start()
  }
  
  def stop() {
    closed = true
    Util.closeSocket(serverSocket)
    acceptorThread.join()
  }

  val acceptorThread = new Thread(acceptLoop _, "mot-commands-acceptor")

  @volatile var closed = false

  def acceptLoop() {
    try {
      while (true) {
        val socket = serverSocket.accept()
        new Thread(() => processClient(socket), "mot-command-handler-for-" + socket.getRemoteSocketAddress).start()
      }
    } catch {
      case e: IOException if closed => // pass
    }
  }

  def processClient(socket: Socket) = {
    val is = socket.getInputStream
    val os = socket.getOutputStream
    try {
      val req = Source.fromInputStream(is).mkString("")
      val reqParts = if (req.isEmpty) Seq() else req.split(" ").toSeq
      def writer(part: String): Unit = {
        os.write(part.getBytes(StandardCharsets.UTF_8))
        os.write('\n')
      }
      val res = handle(Seq(name), reqParts, writer _)
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

  val subcommands = Seq(
    new Clients(context),
    new ClientConnectors(context),
    new ClientConnector(context),
    new Servers(context),
    new ServerConnections(context),
    new ServerConnection(context),
    new ServerFlows(context))

}

object Commands {
  val liveInterval = 1000
}