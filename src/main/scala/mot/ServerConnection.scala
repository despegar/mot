package mot

import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.BlockingQueue
import mot.message.MessageBase
import mot.message.Heartbeat
import mot.message.MessageFrame
import java.util.concurrent.TimeUnit
import scala.util.control.NonFatal
import java.io.IOException
import com.typesafe.scalalogging.slf4j.Logging
import mot.buffer.WriteBuffer
import mot.buffer.ReadBuffer
import mot.message.ClientHello
import mot.message.ServerHello
import mot.message.Response
import Util.FunctionToRunnable
import java.util.concurrent.atomic.AtomicLong

class ServerConnection(val server: Server, val socket: Socket) extends Logging {

  val from = socket.getRemoteSocketAddress.asInstanceOf[InetSocketAddress]
  val finalized = new AtomicBoolean
  val handler = new ServerConnectionHandler(this)

  val sendingQueue = new LinkedBlockingQueue[(Int, Message)](server.sendingQueueSize)

  val readerThread = new Thread(readerLoop _, s"mot-server-reader-${server.name}<-${from}")
  val writerThread = new Thread(writerLoop _, s"mot-server-writer-${server.name}<-${from}")

  val readBuffer = new ReadBuffer(socket.getInputStream, server.readerBufferSize)
  val writeBuffer = new WriteBuffer(socket.getOutputStream, server.writerBufferSize)

  @volatile var sequence = 0
  var lastMessage = 0L

  var clientName: String = _

  @volatile var receivedRespondable = 0L
  @volatile var receivedUnrespondable = 0L
  @volatile var sentResponses = 0L
  val tooLateResponses = new AtomicLong
  
  def start() {
    server.connections.put(Target(socket.getInetAddress.getHostAddress, socket.getPort), this)
    readerThread.start()
    writerThread.start()
  }

  def finalize(e: Throwable) {
    if (finalized.compareAndSet(false, true)) {
      handler.reportError(e)
      Util.closeSocket(socket)
      server.connections.remove(from)
    }
  }

  def sendResponse(responder: Responder, response: Message) = {
    val now = System.nanoTime()
    if (!responder.isOnTime(now)) {
      tooLateResponses.incrementAndGet()
      val delay = now - responder.expiration
      throw new TooLateException(delay)
    }
    sendingQueue.put((responder.sequence, response))
  }

  def close() = {
    finalize(new ServerClosedException)
    readerThread.join()
    writerThread.join()
  }

  def writerLoop() = {
    try {
      val serverHello = ServerHello(protocolVersion = 1, server.name, maxLength = Short.MaxValue)
      logger.trace("Sending " + serverHello)
      serverHello.writeToBuffer(writeBuffer)
      while (!finalized.get) {
        val outRes = sendingQueue.poll(200, TimeUnit.MILLISECONDS)
        if (outRes != null) {
          val (seq, msg) = outRes
          /*
           * There was something in the queue.
           * Note that it is not necessary to check for timeouts, as it was already checked when the response
           * was enqueued, and there is no flow control in responses, everything is delivered as it arrives,
           * so messages do not spend too much time in the queue.
           */
          sendMessage(seq, msg)
          if (sendingQueue.isEmpty)
            writeBuffer.flush()
        } else {
          /*
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
        logger.info(s"IO exception writing: ${e.getMessage}. Possibly some responses got lost.")
        finalize(e)
    }
  }

  def sendMessage(sequence: Int, msg: Message) = {
    val response = Response(sequence, msg.attributes, msg.bodyParts)
    logger.trace("Sending " + response)
    response.writeToBuffer(writeBuffer)
    sentResponses += 1
    lastMessage = System.nanoTime()
  }

  def readerLoop() = {
    try {
      ReaderUtil.prepareSocket(socket)
      val message = MessageBase.readFromBuffer(readBuffer, server.requestMaxLength)
      logger.trace("Read " + message)
      message match {
        case clientHello: ClientHello => processHello(clientHello)
        case any => throw new BadDataException("Unexpected message type: " + any.getClass.getName)
      }
      while (!finalized.get) {
        val message = MessageBase.readFromBuffer(readBuffer, server.requestMaxLength)
        val now = System.nanoTime()
        logger.trace("Read " + message)
        message match {
          case m: Heartbeat => // pass
          case messageFrame: MessageFrame => processMessage(now, messageFrame)
          case any => throw new BadDataException("Unexpected message type: " + any.getClass.getName)
        }
      }
    } catch {
      case e: UncompatibleProtocolVersion =>
        logger.error(s"Uncompatible protocol version: ${e.getMessage} (non-fatal: client should reconnect)")
        finalize(e)
      case e: BadDataException =>
        logger.error(s"Bad data read from connection: ${e.getMessage} (non-fatal: client should reconnect)")
        finalize(e)
      case e: IOException =>
        logger.error("IO exception while reading (non-fatal: client should reconnect)", e)
        finalize(e)
      case NonFatal(e) =>
        logger.error("Unexpected error (bug) in reader loop (non-fatal: client should reconnect)", e)
        finalize(e)
    }
  }

  def processHello(helloMessage: ClientHello) = {
    // TODO: Ver de tolerar versiones nuevas
    if (helloMessage.protocolVersion > Protocol.ProtocolVersion)
      throw new UncompatibleProtocolVersion(s"read ${helloMessage.protocolVersion}, must be ${Protocol.ProtocolVersion}")
    clientName = helloMessage.sender
  }

  def processMessage(now: Long, message: MessageFrame) = {
    val body = message.bodyParts.head // Incoming messages only have one part
    val responder = if (message.respondable) {
      receivedRespondable += 1
      Some(new Responder(handler, sequence, now, message.timeout))
    } else {
      receivedUnrespondable += 1
      None
    }
    val incomingMessage = IncomingMessage(responder, from, clientName, Message.fromByteBuffer(message.attributes, body))
    sequence += 1
    offer(server.receivingQueue, incomingMessage, finalized)
  }

  def offer[A](queue: BlockingQueue[A], element: A, subscriberClosed: AtomicBoolean) {
    var inserted = false
    while (!inserted && !subscriberClosed.get) {
      inserted = queue.offer(element, 100, TimeUnit.MILLISECONDS)
    }
  }

}