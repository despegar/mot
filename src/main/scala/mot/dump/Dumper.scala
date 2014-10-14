package mot.dump

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
import java.nio.charset.StandardCharsets.UTF_8
import mot.Connection

case class Listener(bufferSize: Int) {
  val queue = new LinkedBlockingQueue[MessageEvent](bufferSize) 
  val overflows = new AtomicLong
}

class Dumper(dumperPort: Int) extends StrictLogging {

  def dump(connection: Connection, direction: Direction.Value, msg: MessageBase) = {
    val listeners = currentListeners.keySet
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

  val parser = new DumpFilterParser
  
  class ArgumentError(msg: String) extends Exception(msg)
  
  def processClient(socket: Socket) = {
    import StandardCharsets.UTF_8
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
          case parser.NoSuccess((msg, error)) => throw new ArgumentError(s"Error parsing expression: $msg")
        }
      }
      val filter = filterOpt.getOrElse(AllFilter)
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
        var dumped = 0L
        var processed = 0L
        while (!finished) {
          val event = listener.queue.take()
          if (filter.filter(event)) {
            event.print(os, sdf, showBody, showBodyLength, showAttributes)
            dumped += 1
          }
          processed += 1
        }
        // EOF received, print summary
        os.write(s"$processed messages processed\n".getBytes(UTF_8))
        os.write(s"$dumped messages dumped\n".getBytes(UTF_8))
        os.write(s"${listener.overflows} messages dropped\n".getBytes(UTF_8))
      } finally {
        currentListeners.remove(listener)
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
      socket.close()
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