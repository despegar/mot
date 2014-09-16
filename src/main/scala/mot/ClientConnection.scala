package mot

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
import scala.concurrent.promise
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Future
import java.util.concurrent.TimeoutException
import com.typesafe.scalalogging.slf4j.StrictLogging

class ClientConnection(val connector: ClientConnector, val socket: Socket) extends StrictLogging {

  val readBuffer = new ReadBuffer(socket.getInputStream, connector.client.readerBufferSize)
  val writeBuffer = new WriteBuffer(socket.getOutputStream, connector.client.writerBufferSize)

  val readerThread = new Thread(readerLoop _, s"mot(${connector.client.name})-reader-for-${connector.target}")

  val pendingResponses =
    new ConcurrentHashMap[Int /* sequence */ , PendingResponse](
      1000 /* initial capacity */ ,
      0.5f /* load factor */ ,
      3 /* concurrency level: one thread adding values, one removing, one expiring */ )

  val closed = new AtomicBoolean

  private val serverHelloPromise = promise[ServerHello]
  private val serverHelloFuture = serverHelloPromise.future

  def serverName() = serverHelloFuture.value.map(_.get.name)
  def requestMaxLength() = serverHelloFuture.value.map(_.get.maxLength)

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
    connector.promiseExpirator.stop()
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
        message match {
          case _: Heartbeat => // pass
          case response: Response => processMessage(response)
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
    serverHelloPromise.success(serverHello)
  }

  def processMessage(response: Response) = {
    val body = response.bodyParts.head // Incoming messages only have one part
    pendingResponses.remove(response.requestReference) match {
      case pendingResponse: PendingResponse =>
        val msg = Message(response.attributes, body :: Nil /* use :: to avoid mutable builders */ )
        if (pendingResponse.fulfill(msg))
          connector.responsesReceivedCounter += 1
      case null =>
        logger.trace("Unexpected response arrived (probably expired and then collected): " + response)
    }
  }

  def writerLoop() {
    try {
      val clientHello = ClientHello(1, connector.client.name, Short.MaxValue)
      logger.trace("Sending " + clientHello)
      clientHello.writeToBuffer(writeBuffer)
      writeBuffer.flush()
      val serverHello = Protocol.wait(serverHelloFuture, stop = closed).getOrElse(throw new ClientClosedException)
      while (!closed.get) {
        connector.sendingQueue.poll(200, TimeUnit.MILLISECONDS) match {
          case (message, optionalPromise) =>
            /*
             * There was something in the queue
             */
            sendMessage(message, optionalPromise, serverHello.maxLength)
            if (connector.sendingQueue.isEmpty)
              writeBuffer.flush()
          case null =>
            /*
             * Nothing in the queue after some time, send heart beat.
             * The purpose of heart beats is to keep the wire active where there are no messages.
             * This is useful for detecting dropped connections and avoiding read timeouts in the other side.
             */
            val now = System.nanoTime()
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
        logger.error(s"IO exception while writing: ${e.getMessage()}. Possibly some messages got lost. Reconnecting.")
        reportError(e)
      case e: ClientClosedException =>
        logger.error("client closed while writing. Possibly some messages got lost. Reconnecting.")
        reportError(e)
    }
  }

  private def sendMessage(msg: Message, pendingResponse: Option[PendingResponse], maxLength: Int) {
    if (msg.bodyLength > maxLength) {
      /* 
      * The client is trying to send a message larger than the maximum allowed by the server. If the message is respondable
      * it is possible to let the client know using the promise. If the message is unrespondable, the only thing we can do
      * is log the error.
      */
      val exception = new MessageTooLargeException(msg.bodyLength, maxLength)
      pendingResponse match {
        case Some(pr) =>
          if (pr.error(exception)) {
            // condition is true when timeout did not "win"
            connector.triedToSendTooLargeMessage += 1
          } else {
            connector.expiredInQueue += 1
          }
        case None =>
          logger.info(exception.getMessage)
          connector.triedToSendTooLargeMessage += 1
      }
    } else {
      pendingResponse match {
        case Some(pr) =>
          // Message is respondable
          if (pr.markSent(this, msgSequence)) {
            // Message has not expired
            connector.respondableSentCounter += 1
            doSendMessage(MessageFrame(true, pr.timeoutMs, msg.attributes, msg.bodyLength, msg.bodyParts))
          } else {
            connector.expiredInQueue += 1
          }
        case None =>
          // Message is unrespondable
          connector.unrespondableSentCounter += 1
          doSendMessage(MessageFrame(false, 0, msg.attributes, msg.bodyLength, msg.bodyParts))
      }
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