package mot.monitoring

import com.typesafe.scalalogging.slf4j.Logging
import java.net.ServerSocket
import java.net.InetSocketAddress
import mot.Util.FunctionToRunnable
import scala.io.Source
import scala.util.control.NonFatal
import java.net.SocketException
import java.io.PrintStream
import java.net.Socket

class Commands extends Logging with MultiCommandHandler {

  val serverSocket = new ServerSocket()

  def start() = {
    serverSocket.bind(new InetSocketAddress(4001))
    new Thread(doIt _, "mot-commands-acceptor").start()
  }

  def doIt() {
    while (true) {
      val socket = serverSocket.accept()
      new Thread(() => processClient(socket), "mot-command-handler-for-" + socket.getRemoteSocketAddress).start()
    }
  }

  def processClient(socket: Socket) = {
    var streamming = false
    val is = socket.getInputStream
    val os = socket.getOutputStream
    try {
      val req = Source.fromInputStream(is).mkString("")
      val reqParts = if (req.isEmpty) Seq() else req.split(" ").toSeq
      def writer(part: String): Unit = {
        streamming = true
        os.write(part.getBytes)
        os.write("\n".getBytes)
      }
      val res = handle(Seq(name), reqParts, writer)
      if (streamming)
        throw new Exception("Cannot return if streamming")
      os.write(res.getBytes)
      os.write("\n".getBytes)
    } catch {
      case e: SocketException =>
        logger.info(s"Client ${socket.getRemoteSocketAddress} gone (${e.getMessage})")
      case NonFatal(e) =>
        logger.error("Error processing message", e)
        try {
          if (!streamming) {
            val ps = new PrintStream(os)
            e.printStackTrace(ps)
            ps.flush()
          }
        } catch {
          case NonFatal(e) => logger.error("Could not send message in catch block", e)
        }
    } finally {
      socket.close()
    }
  }

  val helpLine = "Conductor command-line interface."

  val name = "cnd"

  val subcommands = Seq(
    new ClientConnections,
    new Servers,
    new ServerConnections)

}