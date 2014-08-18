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

class ClientConnection(val connector: ClientConnector, val socket: Socket) extends Logging {

  private val readBuffer = new ReadBuffer(socket.getInputStream, connector.client.readerBufferSize)
  private val writeBuffer = new WriteBuffer(socket.getOutputStream, connector.client.writerBufferSize)

  val readerThread = new Thread(readerLoop _, s"mot-client-reader-${connector.client.name}->${connector.target}")

  val promiseExpirator = {
    val tf = new ThreadFactory {
      def newThread(r: Runnable) = 
        new Thread(r, "mot-client-promise-expiratior-${connector.client.name}->${connector.target}")
    }
    new ScheduledThreadPoolExecutor(1, tf)
  }

  val pendingPromises =
    new ConcurrentHashMap[Int /* sequence */ , (ResponsePromise, ScheduledFuture[_] /* expiration task */ )](
      128 /* initial capacity */ ,
      0.75f /* default load factor */ ,
      3 /* concurrency level: one thread adding values, one removing, one expiring */ )

  val closed = new AtomicBoolean

  var maxLength = -1

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
    promiseExpirator.shutdown()
  }

  def readerLoop() = {
    try {
      ReaderUtil.prepareSocket(socket)
      val message = MessageBase.readFromBuffer(readBuffer, connector.client.responseMaxLength)
      logger.trace("Read " + message)
      processHello(message)
      while (!closed.get) {
        val message = MessageBase.readFromBuffer(readBuffer, connector.client.responseMaxLength)
        logger.trace("Read " + message)
        processMessage(message)
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

  def processHello(message: MessageBase) = message match {
    case serverHello: ServerHello =>
      // TODO: Ver de tolerar versiones nuevas
      if (serverHello.protocolVersion > Protocol.ProtocolVersion)
        throw new UncompatibleProtocolVersion(s"read ${serverHello.protocolVersion}, must be ${Protocol.ProtocolVersion}")
      maxLength = serverHello.maxLength
    case any =>
      throw new BadDataException("Unexpected message type: " + any.getClass.getName)
  }

  def processMessage(message: MessageBase) = message match {
    case _: Heartbeat =>
    // pass
    case response: Response =>
      val body = response.bodyParts.head // Incoming messages only have one part
      Option(pendingPromises.remove(response.requestReference)) match {
        case Some((promise, expirationTask)) =>
          expirationTask.cancel(false /* mayInterruptIfRunning */ )
          val delay = promise.delay()
          if (delay <= 0) {
            if (promise.fulfill(Message.fromArrays(response.attributes, body)))
              connector.responsesReceivedCounter.incrementAndGet()
          } else {
            val delayMs = delay / (1000 * 1000)
            logger.debug(s"Expired response (seq: ${response.requestReference}) arrived: arrived $delayMs ms late.")
            if (promise.forget(new ResponseTimeoutException))
              connector.timeoutsCounter.incrementAndGet()
          }
        case None =>
          logger.debug("Unexpected response arrived (probably expired and then collected): " + response)
      }
    case any =>
      throw new BadDataException("Unexpected message type: " + any.getClass.getName)
  }

  def writerLoop() {
    try {
      var lastMessage = 0L
      var msgSequence = 0
      val clientHello = ClientHello(1, connector.client.name, Short.MaxValue)
      logger.trace("Sending " + clientHello)
      clientHello.writeToBuffer(writeBuffer)
      while (!closed.get) {
        Option(connector.sendingQueue.poll(200, TimeUnit.MILLISECONDS)) match {
          case Some((message, optionalPromise)) =>
            optionalPromise match {
              case Some(promise) =>
                // Message is respondable
                val seq = msgSequence // capture for use inside closure
                def forget() = {
                  logger.trace("Expiring pending response, sequence: " + seq)
                  Option(pendingPromises.remove(seq)).foreach {
                    case (p, _) =>
                      if (p.forget(new ResponseTimeoutException))
                        connector.timeoutsCounter.incrementAndGet()
                  }
                }
                val expirationTask = promiseExpirator.schedule(forget _, promise.timeoutMs, TimeUnit.MILLISECONDS)
                pendingPromises.put(msgSequence, (promise, expirationTask))
                connector.respondableSentCounter.incrementAndGet()
              case None =>
                // Message is unrespondable
                connector.unrespondableSentCounter.incrementAndGet()
            }
            val timeout = optionalPromise.map(_.timeoutMs).getOrElse(0)
            val respondable = optionalPromise.isDefined
            val messageFrame = MessageFrame(respondable, timeout, message.attributes, message.bodyParts.map(_.array))
            logger.trace("Sending " + messageFrame)
            messageFrame.writeToBuffer(writeBuffer)
            msgSequence += 1
            lastMessage = System.nanoTime()
          case None =>
            /*
             * The purpose of heart beats is to keep the wire active where there are no messages.
             * This is useful for detecting dropped connections and avoiding read timeouts in the other side.
             */
            if (System.nanoTime() - lastMessage >= Protocol.HeartBeatInterval) {
              val heartbeat = Heartbeat()
              logger.trace("Sending " + heartbeat)
              heartbeat.writeToBuffer(writeBuffer)
              lastMessage = System.nanoTime()
            }
            // Do not let the buffer contents without flushing too much time
            if (writeBuffer.hasData)
              writeBuffer.flush()
        }
      }
    } catch {
      case e: IOException =>
        logger.error("IO exception while writing. Possibly some messages got lost. Reconnecting.", e)
        reportError(e)
    }
  }

  private def forgetAllPromises(cause: Throwable) = {
    logger.debug("Forgetting all promises of client connection: " + socket.getLocalSocketAddress())
    for ((promise, expirationTask) <- pendingPromises.values) {
      expirationTask.cancel(false /* mayInterruptIfRunning */ )
      promise.forget(new InvalidClientConnectionException(cause))
    }
  }

}