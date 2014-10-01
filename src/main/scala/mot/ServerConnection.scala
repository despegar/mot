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
import mot.buffer.WriteBuffer
import mot.buffer.ReadBuffer
import mot.message.ClientHello
import mot.message.ServerHello
import mot.message.Response
import Util.FunctionToRunnable
import java.util.concurrent.atomic.AtomicLong
import scala.collection.immutable
import scala.concurrent.promise
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.atomic.AtomicReference
import com.typesafe.scalalogging.slf4j.StrictLogging
import mot.message.Hello

class ServerConnection(val server: Server, val socket: Socket) extends StrictLogging {

  val from = Address(socket.getInetAddress.getHostAddress, socket.getPort)
  
  val finalized = new AtomicBoolean
  val exception: AtomicReference[Throwable] = new AtomicReference
  
  val handler = new ServerConnectionHandler(this)

  val sendingQueue = new LinkedBlockingQueue[(Int, Message)](server.sendingQueueSize)

  val readerThread = new Thread(readerLoop _, s"mot(${server.name})-reader-for-${from}")
  val writerThread = new Thread(writerLoop _, s"mot(${server.name})-writer-for-${from}")

  val readBuffer = new ReadBuffer(socket.getInputStream, server.readerBufferSize)
  val writeBuffer = new WriteBuffer(socket.getOutputStream, server.writerBufferSize)

  @volatile var sequence = 0
  var lastWrite = 0L
  
  @volatile var lastReception = System.nanoTime() // initialize as recently used

  private val clientHelloPromise = promise[ClientHello]
  private val clientHelloFuture = clientHelloPromise.future 

  @volatile var receivedRespondable = 0L
  @volatile var receivedUnrespondable = 0L
  @volatile var sentResponses = 0L
  val tooLateResponses = new AtomicLong 
  val tooLargeResponses = new AtomicLong

  def clientName() = clientHelloFuture.value.map(_.get.sender)
  def responseMaxLength() = clientHelloFuture.value.map(_.get.maxLength)
  
  logger.info("Accepted connection from " + from)
  
  def start() {
    server.connections.put(from, this)
    readerThread.start()
    writerThread.start()
  }

  def finalize(e: Throwable) {
    if (exception.compareAndSet(null, e)) {
      finalized.set(true)
      logger.debug(s"Finalizing server connection from $from")
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
    if (response.bodyLength > responseMaxLength.get) {
      tooLargeResponses.incrementAndGet()
      throw new MessageTooLargeException(response.bodyLength, responseMaxLength.get)
    }
    /*
     * It is possible that the connection was closed after the previous check, so block looping and report the eventual close.
     */
    var enqueued = false
    while (!enqueued && !finalized.get)
      enqueued = sendingQueue.offer((responder.sequence, response), 100, TimeUnit.MILLISECONDS)
    if (!enqueued)
      throw new InvalidServerConnectionException(exception.get)
  }

  def close() = {
    finalize(new ServerClosedException)
    readerThread.join()
    writerThread.join()
  }

  def writerLoop() = {
    try {
      val serverHello = ServerHello(protocolVersion = 1, server.name, maxLength = server.requestMaxLength).toHelloMessage
      logger.trace("Sending " + serverHello)
      serverHello.writeToBuffer(writeBuffer)
      writeBuffer.flush()
      val clientHello = Protocol.wait(clientHelloFuture, stop = finalized).getOrElse(throw new ServerClosedException)
      while (!finalized.get) {
        val outRes = sendingQueue.poll(200, TimeUnit.MILLISECONDS)
        if (outRes != null) {
          val (seq, msg) = outRes
          /*
           * There was something in the queue.
           * Note that it is not necessary to check for timeouts, as it was already checked when the response
           * was enqueued, and there is no flow control in responses, everything is delivered as it arrives,
           * so messages do not spend too much time in the queue.
           * Additionally, message length is also not checked here, as that was already done before enqueuing
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
          if (now - lastWrite >= Protocol.HeartBeatIntervalNs) {
            val heartbeat = Heartbeat()
            logger.trace("Sending " + heartbeat)
            heartbeat.writeToBuffer(writeBuffer)
            writeBuffer.flush()
            lastWrite = System.nanoTime()
          }
        }
      }
    } catch {
      case e: IOException =>
        logger.info(s"IO exception writing: ${e.getMessage}. Possibly some responses got lost.")
        finalize(e)
      case e: ServerClosedException =>
        logger.info(s"Server closed while writing. Possibly some responses got lost.")
        finalize(e)
    }
  }

  def sendMessage(sequence: Int, msg: Message) = {
    val response = Response(sequence, msg.attributes, msg.bodyLength, msg.bodyParts)
    logger.trace("Sending " + response)
    response.writeToBuffer(writeBuffer)
    sentResponses += 1
    lastWrite = System.nanoTime()
  }

  def readerLoop() = {
    try {
      ReaderUtil.prepareSocket(socket)
      MessageBase.readFromBuffer(readBuffer, server.requestMaxLength) match {
        case hello: Hello => processHello(hello)
        case any => throw new BadDataException("Unexpected message type: " + any.getClass.getName)
      }
      while (!finalized.get) {
        MessageBase.readFromBuffer(readBuffer, server.requestMaxLength) match {
          case m: Heartbeat => // pass
          case messageFrame: MessageFrame => processMessage(messageFrame)
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
        logger.info("IO exception while reading (non-fatal: client should reconnect): " + e.getMessage)
        finalize(e)
      case NonFatal(e) =>
        logger.error("Unexpected error (bug) in reader loop (non-fatal: client should reconnect)", e)
        finalize(e)
    }
  }

  def processHello(hello: Hello) = {
    val clientHello = ClientHello.fromHelloMessage(hello)
    // This is version 1, be future proof and allow greater versions
    clientHelloPromise.success(clientHello)
  }

  def processMessage(frame: MessageFrame) = {
    val body = frame.bodyParts.head // Incoming messages only have one part
    val responder = if (frame.respondable) {
      receivedRespondable += 1
      val now = System.nanoTime()
      Some(new Responder(handler, sequence, now, frame.timeout))
    } else {
      receivedUnrespondable += 1
      None
    }
    val message = Message(frame.attributes, body :: Nil  /* use :: to avoid mutable builders */)
    val incomingMessage = IncomingMessage(responder, from, clientName.get, server.requestMaxLength, message)
    sequence += 1
    lastReception = System.nanoTime()
    offer(server.receivingQueue, incomingMessage, finalized)
  }

  def offer[A](queue: BlockingQueue[A], element: A, subscriberClosed: AtomicBoolean) {
    var inserted = false
    while (!inserted && !subscriberClosed.get) {
      inserted = queue.offer(element, 100, TimeUnit.MILLISECONDS)
    }
  }

}