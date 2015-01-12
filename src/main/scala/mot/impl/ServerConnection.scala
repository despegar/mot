package mot.impl

import java.net.Socket
import java.util.concurrent.BlockingQueue
import mot.protocol.Frame
import mot.protocol.RequestFrame
import java.util.concurrent.TimeUnit
import mot.protocol.ResponseFrame
import mot.util.Util.FunctionToRunnable
import java.util.concurrent.atomic.AtomicLong
import mot.protocol.HelloFrame
import mot.util.UnaryPromise
import mot.protocol.MessageFrame
import mot.Message
import mot.Server
import mot.MessageTooLargeException
import mot.IncomingMessage
import mot.Responder
import java.util.concurrent.atomic.AtomicBoolean
import mot.dump.ConnectionEvent
import mot.dump.Direction
import mot.dump.Operation
import mot.protocol.FlowControlFrame

class ServerConnection(val server: Server, socketImpl: Socket) extends AbstractConnection(server, socketImpl) {

  val handler = new ServerConnectionHandler(this)

  val responseFlows = new ResponseFlows(this)

  val readerThread = new Thread(readerLoop _, s"mot(${server.name})-reader-for-$remoteAddress")
  val writerThread = new Thread(writerLoop _, s"mot(${server.name})-writer-for-$remoteAddress")

  private val helloPromise = new UnaryPromise[ClientHello]

  val remoteHelloLatch = helloPromise.latch

  def remoteNameOption() = helloPromise.value.map(_.sender)
  def remoteMaxLength() = helloPromise.value.map(_.maxLength)

  // Need not be atomic as the are incremented only in connection thread
  @volatile var receivedRespondable = 0L
  @volatile var receivedUnrespondable = 0L
  @volatile var sentResponses = 0L

  // Need be atomic as it is incremented from user threads
  val tooLargeResponses = new AtomicLong

  logger.info("Accepted connection from " + remoteAddress)

  def flow(flowId: Int) = 
    responseFlows.flow(flowId).getOrElse(throw new IllegalStateException("inexistent flow"))
    
  def start(): Unit = {
    party.context.dumper.dump(ConnectionEvent(this, Direction.Incoming, Operation.Creation))
    server.connections.put(remoteAddress, this)
    readerThread.start()
    writerThread.start()
  }

  def reportClose(e: Throwable): Unit = {
    handler.reportError(e)
    server.connections.remove(remoteAddress)
  }

  def offerResponse(serverFlowId: Int, outgoingResponse: OutgoingResponse, wait: Long, timeUnit: TimeUnit): Boolean = {
    if (outgoingResponse.message.bodyLength > remoteMaxLength.get) {
      tooLargeResponses.incrementAndGet()
      throw new MessageTooLargeException(outgoingResponse.message.bodyLength, remoteMaxLength.get)
    }
    val flow = responseFlows.getOrCreateFlow(serverFlowId)
    flow.queue.offer(outgoingResponse, wait, timeUnit)
  }

  def localHello = ServerHello(protocolVersion = 1, localName, party.maxAcceptedLength)

  def outgoingQueue = responseFlows.multiQueue

  def processOutgoing(event: OutgoingEvent): Unit = event match {
    case outRes: OutgoingResponse =>
      val msg = outRes.message
      writeMessage(ResponseFrame(outRes.requestId, msg.attributes, msg.bodyLength, msg.bodyParts))
      sentResponses += 1
  }

  def processHello(hello: HelloFrame): Unit = {
    val clientHello = ClientHello.fromHelloMessage(hello)
    // This is version 1, be future proof and allow greater versions
    helloPromise.complete(clientHello)
  }

  val processIncoming: PartialFunction[Frame, Unit] = {
    case message: MessageFrame => processMessage(message)
    case request: RequestFrame => processRequest(request)
    case flowControl: FlowControlFrame => responseFlows.updateFlow(flowControl.flowId, flowControl.open)
  }

  def processMessage(frame: MessageFrame): Unit = {
    val body = frame.body.head // Incoming messages only have one part
    receivedUnrespondable += 1
    val hello = helloPromise.result
    val message = new Message(frame.attributes, body.length, body :: Nil)
    val incoming = IncomingMessage(None, remoteAddress, localAddress, hello.sender, hello.maxLength, message)
    offer(server.receivingQueue, incoming, isClosing _)
  }

  def processRequest(frame: RequestFrame): Unit = {
    val body = frame.body.head // Incoming messages only have one part
    receivedRespondable += 1
    val responder = Some(new Responder(handler, frame.requestId, frame.timeout, frame.flowId))
    val message = new Message(frame.attributes, body.length, body :: Nil)
    val hello = helloPromise.result
    val incoming = IncomingMessage(responder, remoteAddress, localAddress, hello.sender, hello.maxLength, message)
    offer(server.receivingQueue, incoming, isClosing _)
  }

  def offer[A](queue: BlockingQueue[A], element: A, subscriberClosed: () => Boolean): Unit = {
    var inserted = false
    while (!inserted && !subscriberClosed()) {
      inserted = queue.offer(element, 300, TimeUnit.MILLISECONDS)
    }
  }

}