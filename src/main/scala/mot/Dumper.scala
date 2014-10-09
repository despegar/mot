package mot

import mot.message.MessageBase
import java.net.ServerSocket
import java.net.InetSocketAddress
import mot.Util.FunctionToRunnable
import java.net.Socket
import java.net.SocketException
import com.typesafe.scalalogging.slf4j.StrictLogging
import scala.util.control.NonFatal
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import scala.collection.JavaConversions._
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import scala.io.Source
import java.io.OutputStream
import java.io.IOException

object Direction extends Enumeration {
  val Incoming, Outgoing = Value
}

case class MessageEvent(timestampMs: Long, conn: Connection, direction: Direction.Value, message: MessageBase) {
  
  def print(os: OutputStream, sdf: SimpleDateFormat, showBody: Boolean, showBodyLength: Int) = {
    val arrow = direction match {
      case Direction.Incoming => '<'
      case Direction.Outgoing => '>'
    }
    val local = formatAddress(conn.localAddress)
    val remote = formatAddress(conn.remoteAddress)
    val firstLine = s"${sdf.format(timestampMs)} $local $arrow $remote $message\n"
    os.write(firstLine.getBytes(StandardCharsets.UTF_8))
    if (showBody) {
      var remaining = showBodyLength
      for (buffer <- message.body) {
        val show = math.min(remaining, buffer.limit)
        os.write(buffer.array, buffer.arrayOffset, show)
        remaining -= show
      }
      os.write('\n')
    }
  }
  
  def formatAddress(address: InetSocketAddress) = {
    address.getAddress.getHostAddress + ":" + address.getPort
  }
  
}

class Listener(bufferSize: Int) {
  val queue = new LinkedBlockingQueue[MessageEvent](bufferSize) 
  val overflows = new AtomicLong
  def shouldDump(msg: MessageBase) = true // dump everything, no filters implemented
}

class Dumper(dumperPort: Int) extends StrictLogging {

  def dump(connection: Connection, direction: Direction.Value, msg: MessageBase) = {
    val listeners = currentListeners.keys.filter(_.shouldDump(msg))
    if (!listeners.isEmpty) {
      // do not construct event if there are no listeners
      val event = MessageEvent(System.currentTimeMillis(), connection, direction, msg)
      for (listener <- listeners) { 
        val success = listener.queue.offer(event)
        if (!success)
          listener.overflows.incrementAndGet()
      }
    }
  }
  
  val currentListeners = new ConcurrentHashMap[Listener, Boolean]
  
  val serverSocket = new ServerSocket()

  def start() = {
    serverSocket.bind(new InetSocketAddress(dumperPort))
    new Thread(doIt _, "mot-commands-acceptor").start()
  }

  def doIt() {
    while (true) {
      val socket = serverSocket.accept()
      new Thread(() => processClient(socket), "mot-dump-handler-for-" + socket.getRemoteSocketAddress).start()
    }
  }

  def processClient(socket: Socket) = {
    import StandardCharsets.UTF_8
    val is = socket.getInputStream
    val os = socket.getOutputStream
    try {
      // read lines until empty one
      val lines = Source.fromInputStream(is).getLines.takeWhile(!_.isEmpty).toSeq
      val params = parseParameters(lines)
      val showBody = params.get("body").map(_.toBoolean).getOrElse(false)
      val showBodyLength = params.get("length").map(_.toInt).getOrElse(1024)
      val bufferSize = params.get("buffer-size").map(_.toInt).getOrElse(10000)
      val listener = new Listener(bufferSize)
      currentListeners.put(listener, true)
      try {
        @volatile var finished = false
        def eofReader() = try {
          // closing input stream is used as a signal to tell the server to stop sending the dump,
          // this way the server has the opportunity to send a summary at the end.
          val c = is.read()
          if (c == -1)
            finished = true
          else
            logger.error("Unexpected byte in input stream: " + c)
        } catch {
          case e: IOException => logger.error("Unexpected error readinginput stream: " + e.getMessage)
        }
        new Thread(eofReader _, "mot-dump-eof-reader-for-" + socket.getRemoteSocketAddress).start()
        val sdf = new SimpleDateFormat("HH:mm:ss.SSS'Z'")
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
        var counter = 0L
        while (!finished) {
          val msg = listener.queue.take()
          msg.print(os, sdf, showBody, showBodyLength)
          counter += 1
        }
        // EOF received, print summary
        os.write(s"${counter + listener.overflows.get} messages capured\n".getBytes(UTF_8))
        os.write(s"$counter messages dumped\n".getBytes(UTF_8))
        os.write(s"${listener.overflows.get} messages dropped\n".getBytes(UTF_8))
      } finally {
        currentListeners.remove(listener)
      }
    } catch {
      case e: SocketException =>
        logger.info(s"Client ${socket.getRemoteSocketAddress} gone (${e.getMessage})")
      case NonFatal(e) =>
        logger.error("Error dumping messages", e)
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

  def parseParameters(lines: Seq[String]) = {
    val pairs = for (line <- lines) yield {
      val parts = line.split("=").toSeq
      if (parts.size != 2)
        throw new Exception("Invalid line: " + line)
      val Seq(key, value) = parts
      (key, value)
    }
    pairs.toMap
  }
  
}