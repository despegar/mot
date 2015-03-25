package mot.impl

import java.io.IOException
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import com.typesafe.scalalogging.slf4j.StrictLogging
import mot.ByeException
import mot.CounterpartyClosedException
import mot.LocalClosedException
import mot.MotParty
import mot.ResetException
import mot.buffer.ReadBuffer
import mot.buffer.WriteBuffer
import mot.dump.Direction
import mot.dump.MotEvent
import mot.dump.Operation
import mot.dump.TcpEvent
import mot.protocol.ByeFrame
import mot.protocol.Frame
import mot.protocol.HeartbeatFrame
import mot.protocol.HelloFrame
import mot.protocol.ProtocolException
import mot.protocol.ProtocolSemanticException
import mot.protocol.ResetFrame
import mot.protocol.UnknownFrame
import mot.queue.Pollable
import mot.util.RichSocket
import mot.util.NoStackTraceException
import java.util.concurrent.atomic.AtomicLong

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

  val readBuffer = new ReadBuffer(socket.impl.getInputStream, party.readBufferSize)
  val writeBuffer = new WriteBuffer(socket.impl.getOutputStream, party.writeBufferSize)

  val exception = new AtomicReference[Throwable]

  def setException(e: Throwable) = exception.compareAndSet(null, e)

  val lastRead = new AtomicLong(System.nanoTime())
  val lastWrite = new AtomicLong(System.nanoTime())

  def isClosing() = exception.get != null

  def readFrame(): Frame = {
    val msg = Frame.read(readBuffer, party.maxLength)
    party.context.dumper.dump(MotEvent(this, Direction.Incoming, msg))
    lastRead.lazySet(System.nanoTime())
    msg
  }

  def writeFrame(msg: Frame): Unit = {
    party.context.dumper.dump(MotEvent(this, Direction.Outgoing, msg))
    msg.write(writeBuffer)
    lastWrite.lazySet(System.nanoTime())
  }

  def close(): Unit = {
    setException(new LocalClosedException)
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

  /*
   * Immediately sending all outgoing messages can cause to many system calls with memory copying (socket writes). On
   * the other hand, to long a buffering can reduce response time. 
   */
  private val maxBufferingTimeNs = Duration(100, TimeUnit.MICROSECONDS).toNanos
  
  private val pollingTimeoutNs = Duration(1, TimeUnit.SECONDS).toNanos
    
  def writerLoop(): Unit = {
    class GreetingAbortedException extends Exception with NoStackTraceException
    try {
      writeFrame(localHello.toHelloMessage)
      writeBuffer.flush()
      try {
        if (!wait(remoteHelloLatch, stop = isClosing _))
          throw new GreetingAbortedException
        while (!isClosing) {
          outgoingQueue.pollWithRemaining(pollingTimeoutNs, TimeUnit.NANOSECONDS) match {
            case (event: OutgoingEvent, remaining) =>
              processOutgoing(event)
              if (remaining == 0 || System.nanoTime() - writeBuffer.lastFlush > maxBufferingTimeNs)
                writeBuffer.flush()
            case (null, _) =>
              /*
               * The purpose of heart beats is to keep the wire active where there are no messages.
               * This is useful for detecting dropped connections and avoiding read timeouts in the other side.
               */
              if (System.nanoTime() - lastWrite.get >= Protocol.HeartBeatIntervalNs) {
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