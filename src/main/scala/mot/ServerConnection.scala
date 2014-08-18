package mot

import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
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

class ServerConnection(val server: Server, val socket: Socket) extends Logging {

  val from = socket.getRemoteSocketAddress.asInstanceOf[InetSocketAddress]
  val finalized = new AtomicBoolean
  val handler = new ServerConnectionHandler(this)

  val sendingQueue = new LinkedBlockingQueue[(Int, Message)](server.sendingQueueSize)

  val readerThread = new Thread(readerLoop _, s"mot-server-reader-${server.bindAddress}<-${from}")
  val writerThread = new Thread(writerLoop _, s"mot-server-writer-${server.bindPort}<-${from}")

  val readBuffer = new ReadBuffer(socket.getInputStream, server.readerBufferSize)
  val writeBuffer = new WriteBuffer(socket.getOutputStream, server.writerBufferSize)

  @volatile var sequence = 0

  var clientName: String = _

  def start() {
    server.connectors.put(from, this)
    readerThread.start()
    writerThread.start()
  }
  
  def finalize(e: Throwable) {
    if (finalized.compareAndSet(false, true)) {
      handler.reportError(e)
      Util.closeSocket(socket)
      server.connectors.remove(from)
    }
  }

  def sendResponse(responder: Responder, response: Message) = {
    val now = System.nanoTime()
    val maximum = responder.receptionTime + responder.timeout * 1000 * 1000
    val delay = now - maximum
    if (delay > 0)
      throw new TooLateException(delay)
    sendingQueue.put((responder.sequence, response))
  }

  def close() = {
    finalize(new ServerClosedException)
    readerThread.join()
    writerThread.join()
  }

  def writerLoop() = {
    try {
      val serverHello = ServerHello(protocolVersion = 1, maxLength = Short.MaxValue)
      logger.trace("Sending " + serverHello)
      serverHello.writeToBuffer(writeBuffer)
      var lastMessage = 0L
      while (!finalized.get) {
        val now = System.nanoTime()
        val outRes = sendingQueue.poll(200, TimeUnit.MILLISECONDS)
        if (outRes != null) {
          val (seq, msg) = outRes
          val response = Response(seq, msg.attributes, msg.bodyParts.map(_.array))
          logger.trace("Sending " + response)
          response.writeToBuffer(writeBuffer)
          lastMessage = now
        } else {
          /*
         * The purpose of heart beats is to keep the wire active where there are no messages.
         * This is useful for detecting dropped connections and avoiding read timeouts in the other side.
         */
          if (now - lastMessage >= Protocol.HeartBeatInterval * 1000 * 1000) {
            val heartbeat = Heartbeat()
            logger.trace("Sending " + heartbeat)
            heartbeat.writeToBuffer(writeBuffer)
            lastMessage = now
          }
          // Do not let the buffer contents without flushing too much time
          if (writeBuffer.hasData)
            writeBuffer.flush()
        }
      }
    } catch {
      case e: IOException =>
        logger.info(s"IO exception writing: ${e.getMessage}. Possibly some responses got lost.")
        finalize(e)
    }
  }

  def readerLoop() = {
    try {
      ReaderUtil.prepareSocket(socket)
      val message = MessageBase.readFromBuffer(readBuffer, server.requestMaxLength)
      logger.trace("Read " + message)
      processHello(message)
      while (!finalized.get) {
        val message = MessageBase.readFromBuffer(readBuffer, server.requestMaxLength)
        logger.trace("Read " + message)
        processMessage(message)
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

  def processHello(message: MessageBase) = message match {
    case helloMessage: ClientHello =>
      // TODO: Ver de tolerar versiones nuevas
      if (helloMessage.protocolVersion > Protocol.ProtocolVersion)
        throw new UncompatibleProtocolVersion(s"read ${helloMessage.protocolVersion}, must be ${Protocol.ProtocolVersion}")
      clientName = helloMessage.sender
    case any =>
      throw new BadDataException("Unexpected message type: " + any.getClass.getName)
  }

  def processMessage(message: MessageBase) = message match {
    case m: Heartbeat =>
    // pass
    case m: MessageFrame =>
      val body = m.bodyParts.head // Incoming messages only have one part
      val messageRef = if (m.respondable) 
        Some(new Responder(handler, sequence, System.nanoTime(), m.timeout))
      else
        None
      val message = Message.fromArrays(m.attributes, body)
      val incomingMessage = IncomingMessage(messageRef, from, clientName, message)
      sequence += 1
      offer(server.receivingQueue, incomingMessage, finalized)
    case any =>
      throw new BadDataException("Unexpected message type: " + any.getClass.getName)
  }

  def offer[A](queue: BlockingQueue[A], element: A, subscriberClosed: AtomicBoolean) {
    var inserted = false
    while (!inserted && !subscriberClosed.get) {
      inserted = queue.offer(element, 100, TimeUnit.MILLISECONDS)
    }
  }

}