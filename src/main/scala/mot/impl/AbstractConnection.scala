package mot.impl

import java.net.Socket
import mot.util.RichSocket
import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import mot.dump.Dumper
import mot.protocol.Frame
import mot.dump.MotEvent
import mot.dump.Direction
import mot.LocalClosedException
import mot.protocol.HeartbeatFrame
import com.typesafe.scalalogging.slf4j.StrictLogging
import java.io.EOFException
import java.io.IOException
import mot.dump.Operation
import mot.dump.TcpEvent
import mot.queue.Pollable
import mot.protocol.ProtocolSemanticException
import mot.GreetingAbortedException
import mot.protocol.ByeFrame
import mot.protocol.ResetFrame
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import mot.protocol.HelloFrame
import mot.Context
import mot.ResetException
import mot.CounterpartyClosedException
import mot.protocol.ProtocolException
import scala.util.control.NonFatal
import mot.protocol.UnknownFrame
import mot.ByeException
import java.util.concurrent.CountDownLatch
import mot.MotParty
import java.util.concurrent.atomic.AtomicReference

abstract class AbstractConnection(val party: MotParty, val socketImpl: Socket) extends Connection with StrictLogging {

  def remoteNameOption: Option[String]

  def localHello: HelloMessage
  def remoteHelloLatch: CountDownLatch
  def outgoingQueue: Pollable[OutgoingEvent]

  // Processing callbacks
  def processOutgoing(e: OutgoingEvent): Unit
  def processHello(hello: HelloFrame): Unit
  def processIncoming: PartialFunction[Frame, Unit]

  def reportClose(e: Throwable): Unit

  val socket = new RichSocket(socketImpl)
  val localAddress = socket.localAddress
  val remoteAddress = socket.remoteAddress

  def localName = party.name
  def remoteName = remoteNameOption.getOrElse("")

  val readBuffer = new ReadBuffer(socket.impl.getInputStream, party.readerBufferSize)
  val writeBuffer = new WriteBuffer(socket.impl.getOutputStream, party.writerBufferSize)

  val exception = new AtomicReference[Throwable]

  def setException(e: Throwable) = exception.compareAndSet(null, e)

  @volatile var lastReception = System.nanoTime() // initialize as recently used
  @volatile var lastWrite = 0L

  def isClosing() = exception.get != null

  def readFrame(): Frame = {
    val msg = Frame.read(readBuffer, party.maxAcceptedLength)
    party.context.dumper.dump(MotEvent(this, Direction.Incoming, msg))
    lastReception = System.nanoTime()
    msg
  }

  def writeFrame(msg: Frame): Unit = {
    party.context.dumper.dump(MotEvent(this, Direction.Outgoing, msg))
    msg.write(writeBuffer)
    lastWrite = System.nanoTime()
  }

  def close(): Unit = {
    setException(new LocalClosedException)
  }

  /*
   * The purpose of heart beats is to keep the wire active where there are no messages.
   * This is useful for detecting dropped connections and avoiding read timeouts in the other side.
   */
  private def sendHeartbeatIfNeeded(): Unit = {
    val now = System.nanoTime()
    if (now - lastWrite >= Protocol.HeartBeatIntervalNs)
      writeFrame(HeartbeatFrame())
  }

  private def closeSocket(e: Throwable): Unit = {
    if (socket.closeOnce()) {
      logger.info("Closing connection: " + e.getMessage)
      logger.trace("", e)
      val direction = e match {
        case _ :ProtocolException | _: LocalClosedException => Direction.Outgoing
        case _: IOException | _: CounterpartyClosedException => Direction.Incoming
      }
      party.context.dumper.dump(TcpEvent(this, direction, Operation.Close, e.getMessage))
      reportClose(e)
    }
  }

  private val maxBufferingTimeNanos = Duration(100, TimeUnit.MILLISECONDS).toNanos
  
  def writerLoop(): Unit = {
    try {
      writeFrame(localHello.toHelloMessage)
      writeBuffer.flush()
      try {
        if (!wait(remoteHelloLatch, stop = isClosing _))
          throw new GreetingAbortedException
        while (!isClosing) {
          outgoingQueue.poll(300, TimeUnit.MILLISECONDS) match {
            case event: OutgoingEvent =>
              processOutgoing(event)
              val now = System.nanoTime()
              if (outgoingQueue.isEmpty || now - writeBuffer.lastFlush > maxBufferingTimeNanos)
                writeBuffer.flush()
            case null =>
              val now = System.nanoTime()
              if (now - lastWrite >= Protocol.HeartBeatIntervalNs) {
                writeFrame(HeartbeatFrame())
                writeBuffer.flush()
              }
          }
        }
      } catch {
        case e: GreetingAbortedException => // pass
      }
      exception.get match {
        case e: LocalClosedException => writeFrame(ByeFrame())
        case e: ProtocolSemanticException => writeFrame(ResetFrame(e.getMessage))
        case _ => // pass
      }
      writeBuffer.flush()
    } catch {
      case e: IOException =>
        logger.info(s"IO exception while writing: ${e.getMessage}")
        setException(e)
      case NonFatal(e) =>
        setException(e)
        party.context.uncaughtErrorHandler.handle(e)
    } finally {
      closeSocket(exception.get)
    }
  }

  def wait[A](latch: CountDownLatch, stop: () => Boolean): Boolean = {
    var done = false
    while (!done && !stop()) {
      done = latch.await(200, TimeUnit.MILLISECONDS)
    }
    done
  }

  def readerLoop(): Unit = {
    try {
      prepareSocket(socket.impl)
      readFrame() match {
        case hello: HelloFrame => processHello(hello)
        case reset: ResetFrame => throw new ResetException(reset.error)
        case any: Frame => throw new ProtocolSemanticException(
          s"Unexpected frame: ${any.messageType}. First frame must be a hello")
      }
      while (!isClosing) {
        val frame = readFrame()
        frame match {
          case unknown: UnknownFrame => logger.info("Received unknown frame. Ignoring")
          case hb: HeartbeatFrame => // pass
          case reset: ResetFrame => throw new ResetException(reset.error)
          case bye: ByeFrame => throw new ByeException
          case frame if processIncoming.isDefinedAt(frame) => processIncoming(frame)
          case any: Frame => throw new ProtocolSemanticException("Unexpected frame: " + any.messageType)
        }
      }
    } catch {
      case e: IOException if isClosing =>
      // the writer thread closed, do nothing
      case e: IOException if !isClosing =>
        setException(e)
      case e: CounterpartyClosedException =>
        setException(e)
      case e: ProtocolException =>
        setException(e)
      case NonFatal(e) =>
        setException(e)
        party.context.uncaughtErrorHandler.handle(e)
    }
  }

}