package mot.dump

import mot.protocol.Frame
import java.net.ServerSocket
import java.net.InetSocketAddress
import mot.util.Util.FunctionToRunnable
import java.net.Socket
import java.net.SocketException
import com.typesafe.scalalogging.slf4j.StrictLogging
import scala.util.control.NonFatal
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import scala.collection.JavaConversions._
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import scala.io.Source
import java.io.OutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit
import mot.impl.Connection
import mot.util.Util
import java.net.InetAddress

case class Listener(bufferSize: Int) {
  val queue = new LinkedBlockingQueue[Event](bufferSize)
  val overflows = new AtomicLong
}

final class Dumper(dumperPort: Int) extends StrictLogging {

  def dump(event: Event): Unit = {
    // Optimize the very common case of no listeners
    if (currentListeners.isEmpty)
      return
    // Avoid wrapping in Scala iterators, which add overhead
    val it = currentListeners.keys
    while (it.hasMoreElements) {
      val listener = it.nextElement()
      val success = listener.queue.offer(event)
      if (!success)
        listener.overflows.incrementAndGet()
    }
  }

  val currentListeners = new ConcurrentHashMap[Listener, Boolean]
  val serverSocket = new ServerSocket
  val acceptorThread = new Thread(acceptLoop _, "mot-dump-acceptor")

  @volatile var closed = false

  def start() = {
    serverSocket.bind(new InetSocketAddress(InetAddress.getByName(null) /* loopback interface */, dumperPort))
    acceptorThread.start()
  }

  def stop() {
    closed = true
    Util.closeSocket(serverSocket)
    acceptorThread.join()
  }

  def listen(bufferSize: Int) = {
    val listener = new Listener(bufferSize)
    currentListeners.put(listener, true)
    listener
  }

  def unlisten(listener: Listener): Unit = {
    currentListeners.remove(listener)
  }

  def acceptLoop() {
    try {
      while (true) {
        val socket = serverSocket.accept()
        new Thread(() => processClient(socket), "mot-dump-handler-for-" + socket.getRemoteSocketAddress).start()
      }
    } catch {
      case e: IOException if closed => // closing, exception expected
    }
  }

  val parser = new DumpFilterParser

  class ArgumentError(msg: String) extends Exception(msg)

  def processClient(socket: Socket) = {
    socket.setSoTimeout(100) // localhost should be fast
    val is = socket.getInputStream
    val os = socket.getOutputStream
    try {
      // read lines until empty one
      val lines = Source.fromInputStream(is).getLines.takeWhile(!_.isEmpty).toSeq
      val params = parseParameters(lines)
      val showBody = params.get("body").map(_.toBoolean).getOrElse(false)
      val showAttributes = params.get("attributes").map(_.toBoolean).getOrElse(false)
      val showBodyLength = params.get("length").map(_.toInt).getOrElse(1024)
      val bufferSize = params.get("buffer-size").map(_.toInt).getOrElse(10000)
      val filterOpt = params.get("filter").map { str =>
        parser.parseAll(str) match {
          case parser.Success(result, next) => result
          case parser.NoSuccess((msg, next)) => throw new ArgumentError(s"Error parsing expression: $msg")
        }
      }
      val filter = filterOpt.getOrElse(Filters.All)
      val listener = listen(bufferSize)
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
          case e: IOException => logger.error("Unexpected error reading input stream: " + e.getMessage)
        }
        socket.setSoTimeout(0) // everything read, now wait forever for EOF
        new Thread(eofReader _, "mot-dump-eof-reader-for-" + socket.getRemoteSocketAddress).start()
        val sdf = new SimpleDateFormat("HH:mm:ss.SSS'Z'")
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
        var dumped = 0L
        var processed = 0L
        while (!finished && !closed) {
          val event = listener.queue.poll(200, TimeUnit.MILLISECONDS)
          if (event != null) {
            if (filter.filter(event)) {
              event.print(os, sdf, showBody, showBodyLength, showAttributes)
              dumped += 1
            }
            processed += 1
          }
        }
        // EOF received, print summary
        val summary =
          s"$processed events occured and processed during capture (regardless of current filter)\n" +
            s"${listener.overflows} dropped because the buffer was too small\n" +
            s"$dumped events passed the filter and were dumped\n"
        os.write(summary.getBytes(UTF_8))
      } finally {
        unlisten(listener)
      }
    } catch {
      case e: ArgumentError =>
        logger.info(s"Invalid dump filter: " + e.getMessage)
        os.write(e.getMessage.getBytes(UTF_8))
        os.write('\n')
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
      Util.closeSocket(socket)
    }
  }

  def parseParameters(lines: Seq[String]) = {
    val pairs = for (line <- lines) yield {
      val parts = line.split("=", 2).toSeq
      if (parts.size < 2)
        throw new Exception("Invalid line: " + line)
      val Seq(key, value) = parts
      (key, value)
    }
    pairs.toMap
  }

}