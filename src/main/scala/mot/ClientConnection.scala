package mot

import com.typesafe.scalalogging.slf4j.Logging
import Util.FunctionToRunnable
import mot.buffer.ReadBuffer
import mot.message.MessageBase
import java.io.IOException
import mot.message.Heartbeat
import mot.message.Response
import mot.buffer.WriteBuffer
import java.util.concurrent.ConcurrentHashMap
import mot.message.ClientHello
import scala.concurrent.Promise
import mot.message.MessageFrame
import mot.message.ServerHello
import scala.util.control.NonFatal
import java.net.Socket
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong
import scala.collection.immutable

class ClientConnection(val connector: ClientConnector, val socket: Socket) extends Logging {

  val readBuffer = new ReadBuffer(socket.getInputStream, connector.client.readerBufferSize)
  val writeBuffer = new WriteBuffer(socket.getOutputStream, connector.client.writerBufferSize)

  val readerThread = new Thread(readerLoop _, s"mot(${connector.client.name})-reader-for-${connector.target}")

  val pendingResponses =
    new ConcurrentHashMap[Int /* sequence */ , PendingResponse](
      256 /* initial capacity */ ,
      0.75f /* default load factor */ ,
      3 /* concurrency level: one thread adding values, one removing, one expiring */ )

  val closed = new AtomicBoolean

  @volatile var maxLength: Option[Int] = None
  @volatile var serverName: Option[String] = None

  @volatile var unrespondableSentCounter = 0L
  @volatile var respondableSentCounter = 0L
  @volatile var responsesReceivedCounter = 0L

  var lastMessage = 0L
  var msgSequence = 0

  def isClosed() = closed.get

  def startAndBlockWriting() = {
    readerThread.start()
    writerLoop()
  }

  /**
   * Report an error in the connection. It can be done from either the reading or the writing threads.
   * Propagate the exception to the promises and terminate the thread that did not cause the error.
   */
  def reportError(e: Throwable) {
    if (closed.compareAndSet(false, true)) {
      Util.closeSocket(socket)
      forgetAllPromises(e)
    }
  }

  def close() {
    reportError(new ClientClosedException)
    connector.promiseExpirator.shutdown()
  }

  def readerLoop() = {
    try {
      ReaderUtil.prepareSocket(socket)
      val message = MessageBase.readFromBuffer(readBuffer, connector.client.responseMaxLength)
      logger.trace("Read " + message)
      message match {
        case serverHello: ServerHello => processHello(serverHello)
        case any => throw new BadDataException("Unexpected message type: " + any.getClass.getName)
      }
      while (!closed.get) {
        val message = MessageBase.readFromBuffer(readBuffer, connector.client.responseMaxLength)
        logger.trace("Read " + message)
        val now = System.nanoTime()
        message match {
          case _: Heartbeat => // pass
          case response: Response => processMessage(now, response)
          case any => throw new BadDataException("Unexpected message type: " + any.getClass.getName)
        }
      }
    } catch {
      case e: UncompatibleProtocolVersion =>
        logger.error(s"Uncompatible protocol version: " + e.getMessage)
        reportError(e)
      case e: BadDataException =>
        logger.error(s"Bad data read from connection: " + e.getMessage)
        reportError(e)
      case e: IOException =>
        logger.error("IO exception while reading", e)
        reportError(e)
      case NonFatal(e) =>
        logger.error("Unexpected error (bug) in reader loop", e)
        reportError(e)
    }
  }

  def processHello(serverHello: ServerHello) = {
    // TODO: Ver de tolerar versiones nuevas
    if (serverHello.protocolVersion > Protocol.ProtocolVersion)
      throw new UncompatibleProtocolVersion(s"read ${serverHello.protocolVersion}, must be ${Protocol.ProtocolVersion}")
    maxLength = Some(serverHello.maxLength)
    serverName = Some(serverHello.name)
  }

  def processMessage(now: Long, response: Response) = {
    val body = response.bodyParts.head // Incoming messages only have one part
    Option(pendingResponses.remove(response.requestReference)) match {
      case Some(pendingResponse) =>
        pendingResponse.fulfill(Message(response.attributes, body :: Nil /* use :: to avoid mutable builders */ ))
      case None =>
        logger.trace("Unexpected response arrived (probably expired and then collected): " + response)
    }
  }

  def writerLoop() {
    try {
      val clientHello = ClientHello(1, connector.client.name, Short.MaxValue)
      logger.trace("Sending " + clientHello)
      clientHello.writeToBuffer(writeBuffer)
      while (!closed.get) {
        val dequeued = Option(connector.sendingQueue.poll(200, TimeUnit.MILLISECONDS))
        val now = System.nanoTime()
        dequeued match {
          case Some((message, optionalPromise)) =>
            /*
             * There was something in the queue
             */
            sendMessage(now, message, optionalPromise)
            if (connector.sendingQueue.isEmpty)
              writeBuffer.flush()
          case None =>
            /*
             * Nothing in the queue after some time, send heart beat.
             * The purpose of heart beats is to keep the wire active where there are no messages.
             * This is useful for detecting dropped connections and avoiding read timeouts in the other side.
             */
            if (now - lastMessage >= Protocol.HeartBeatIntervalNs) {
              val heartbeat = Heartbeat()
              logger.trace("Sending " + heartbeat)
              heartbeat.writeToBuffer(writeBuffer)
              writeBuffer.flush()
              lastMessage = System.nanoTime()
            }
        }
      }
    } catch {
      case e: IOException =>
        logger.error("IO exception while writing. Possibly some messages got lost. Reconnecting.", e)
        reportError(e)
    }
  }

  private def sendMessage(now: Long, message: Message, pendingResponse: Option[PendingResponse]) {
    pendingResponse match {
      case Some(pr) =>
        // Message is respondable
        if (pr.markSent(this, msgSequence)) {
          // Message has not expired
          respondableSentCounter += 1
          doSendMessage(MessageFrame(true, pr.timeoutMs, message.attributes, message.bodyParts))
        }
      case None =>
        // Message is unrespondable
        unrespondableSentCounter += 1
        doSendMessage(MessageFrame(false, 0, message.attributes, message.bodyParts))
    }
  }

  private def doSendMessage(msg: MessageFrame) {
    logger.trace("Sending " + msg)
    msg.writeToBuffer(writeBuffer)
    msgSequence += 1
    lastMessage = System.nanoTime()
  }

  private def forgetAllPromises(cause: Throwable) = {
    logger.debug("Forgetting all promises of client connection: " + socket.getLocalSocketAddress)
    for (pendingResponse <- pendingResponses.values) {
      pendingResponse.unscheduleExpiration()
      pendingResponse.promise.tryFailure(new InvalidClientConnectionException(cause))
    }
  }

}