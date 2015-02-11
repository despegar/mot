package mot.impl

import mot.util.Util.FunctionToRunnable
import mot.protocol.Frame
import mot.protocol.ResponseFrame
import java.util.concurrent.ConcurrentHashMap
import mot.protocol.RequestFrame
import java.net.Socket
import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions._
import java.util.concurrent.atomic.AtomicLong
import mot.protocol.HelloFrame
import java.net.InetSocketAddress
import mot.util.UnaryPromise
import mot.protocol.MessageFrame
import mot.Message
import mot.MessageTooLargeException
import mot.InvalidClientConnectionException
import mot.protocol.FlowControlFrame
import mot.dump.TcpEvent
import mot.dump.Direction
import mot.dump.Operation

class ClientConnection(val connector: ClientConnector, socketImpl: Socket)
  extends AbstractConnection(connector.client, socketImpl) {

  val readerThread = new Thread(readerLoop _, s"mot(${connector.client.name})-reader-for-${connector.target}")

  val helloPromise = new UnaryPromise[ServerHello]

  val remoteHelloLatch = helloPromise.latch

  def remoteNameOption() = helloPromise.value.map(_.name)
  def remoteMaxLength() = helloPromise.value.map(_.maxLength)

  def isClosed() = socket.isClosed()

  def startAndBlockWriting(): Unit = {
    party.context.dumper.dump(TcpEvent(this, Direction.Outgoing, Operation.Creation))
    readerThread.start()
    writerLoop()
  }

  def reportClose(cause: Throwable): Unit = {
    logger.debug("Forgetting all promises of client connection: " + socket.impl.getLocalSocketAddress)
    for (pendingResponse <- connector.pendingResponses.values) {
      pendingResponse.error(this, new InvalidClientConnectionException(cause))
    }
  }

  def processHello(hello: HelloFrame): Unit = {
    val serverHello = ServerHello.fromHelloMessage(hello)
    // This is version 1, be future proof and allow greater versions
    helloPromise.complete(serverHello)
  }

  val processIncoming: PartialFunction[Frame, Unit] = {
    case response: ResponseFrame =>
      val body = response.body.head // incoming messages only have one part 
      val message = new Message(response.attributes, body.length, body :: Nil)
      connector.pendingResponses.remove(response.reference) match {
        case res: PendingResponse =>
          res.fulfill(this, message)
          connector.responsesReceivedCounter += 1
        case null =>
          // unexpected response arrived (probably expired and then collected)
      }
  }

  def localHello = ClientHello(protocolVersion = 1, localName, party.maxAcceptedLength)

  def outgoingQueue = connector.sendingQueue

  def processOutgoing(event: OutgoingEvent): Unit = event match {
    case OutgoingMessage(message, pendingRes) => sendMessage(message, pendingRes, remoteMaxLength.get)
    case FlowNotification(flowId, open) => writeMessage(FlowControlFrame(flowId, open))
  }

  private def sendMessage(msg: Message, pendingResponse: Option[PendingResponse], maxLength: Int): Unit = {
    if (msg.bodyLength > maxLength) {
      reportMessageTooLarge(msg, maxLength, pendingResponse)
    } else {
      pendingResponse match {
        case Some(pr) =>
          connector.respondableSentCounter += 1
          val frame = RequestFrame(pr.requestId, pr.flow.id, pr.timeoutMs, msg.attributes, msg.bodyLength, msg.bodyParts)
          writeMessage(frame)
        case None =>
          connector.unrespondableSentCounter += 1
          writeMessage(MessageFrame(msg.attributes, msg.bodyLength, msg.bodyParts))
      }
    }
  }

  /* 
   * The client is trying to send a message larger than the maximum allowed by the server. If the message is 
   * respondable it is possible to let the client know using the promise. If the message is unrespondable, the only 
   * thing we can do is log the error.
   */
  def reportMessageTooLarge(msg: Message, maxLength: Int, pendingResponse: Option[PendingResponse]): Unit = {
    val exception = new MessageTooLargeException(msg.bodyLength, maxLength)
    pendingResponse match {
      case Some(pr) => pr.error(this, exception)
      case None => logger.info(exception.getMessage)
    }
    connector.triedToSendTooLargeMessage += 1
  }

}