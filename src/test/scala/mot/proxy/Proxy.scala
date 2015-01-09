package mot.proxy

import mot.Context
import mot.Client
import mot.Server
import mot.Address
import java.util.concurrent.LinkedBlockingQueue
import mot.Message
import com.typesafe.scalalogging.slf4j.StrictLogging
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import mot.Responder
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.concurrent.ConcurrentHashMap
import mot.ClientFlow
import mot.IncomingResponse
import scala.collection.JavaConversions._
import mot.impl.ServerConnectionHandler
import mot.InvalidServerConnectionException
import mot.util.Util.FunctionToRunnable
import scala.util.control.NonFatal
import javax.xml.ws.Response
import mot.ResponseAlreadySendException
import scala.io.StdIn
import java.util.concurrent.TimeUnit
import mot.IncomingMessage
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ScheduledThreadPoolExecutor
import mot.util.NamedThreadFactory
import scala.concurrent.duration.Duration
import mot.util.ByteArray

/**
 * Mot reverse proxy
 */
object Proxy {

  def main(args: Array[String]) {
    val context = new Context(monitoringPort = args(0).toInt, dumpPort = args(1).toInt)
    val proxy = new Proxy(context)
    proxy.start()
  }

}

class Proxy(val context: Context) extends StrictLogging {

  val name = "proxy"

  val frontend = new Server(
    context,
    name,
    6000,
    receivingQueueSize = 100000,
    sendingQueueSize = 100000,
    readerBufferSize = 200000,
    writerBufferSize = 200000)

  val backend = new Client(
    context,
    name,
    sendingQueueSize = 100000,
    readerBufferSize = 200000,
    writerBufferSize = 200000)

  val responseQueue = new LinkedBlockingQueue[(IncomingResponse, Responder)](100000)
  val backendFlows = new ConcurrentHashMap[Address, ClientFlow]()
  val closedFlows = new ConcurrentHashMap[FlowAssociation, Boolean]()

  val responseOverflow = new AtomicLong
  val messageErrors = new AtomicLong
  
  @volatile var closed = false

  def start() {
    val requestThread1 = new Thread(requestLoop _, "request-1")
    val requestThread2 = new Thread(requestLoop _, "request-2")
    val responseThread = new Thread(responseLoop _, "response")
    val flowMonitorThread = new Thread(flowLoop _, "closed-flows-monitor")
    requestThread1.start()
    requestThread2.start()
    responseThread.start()
    flowMonitorThread.start()
    Console.println("Press return to exit")
    StdIn.readLine()
    context.close()
    closed = true
    requestThread1.join()
    requestThread2.join()
    responseThread.join()
    flowMonitorThread.join()
    flowExpirator.shutdown()

  }

  def getFlow(address: Address) = {
    val existent = backendFlows.get(address)
    if (existent != null) {
      existent
    } else {
      synchronized {
        val flow = backend.createFlow()
        backendFlows.put(address, flow)
        flow
      }
    }
  }

  class RequestError(msg: String) extends Exception(msg)

  def requestLoop() = {
    while (!closed) {
      frontend.poll(200, TimeUnit.MILLISECONDS) match {
        case msg: IncomingMessage =>
          try {
            val proxyAttr = msg.message.firstAttribute("Proxy").getOrElse {
              throw new RequestError("'Proxy' attribute needed")
            }
            val target = try {
              Address.fromString(proxyAttr.asString(US_ASCII))
            } catch {
              case e: IllegalArgumentException => throw new RequestError(e.getMessage)
            }
            val success = msg.responderOption match {
              case Some(resp) =>
                val promise = new ContextualPromise(responseQueue, resp)
                val flow = getFlow(msg.remoteAddress)
                if (flow.isOpen) {
                  backend.offerRequest(target, msg.message, resp.timeoutMs, promise, flow)
                } else {
                  // cannot send messages associated with a closed flow
                  responseOverflow.incrementAndGet()
                  true
                }
              case None =>
                backend.offerMessage(target, msg.message)
            }
            if (!success)
              respondError(msg.responderOption, 503, "Backend busy")
          } catch {
            case e: RequestError =>
              msg.responderOption.map(r => sendErrorIfPossible(r, 400, e.getMessage))
              logger.debug("Request error: " + e.getMessage)
            case NonFatal(e) =>
              msg.responderOption.map(r => sendErrorIfPossible(r, 500, e.getMessage))
              logger.debug("Error", e)
          }
        case null => // pass
      }
    }
  }

  def respondError(responder: Option[Responder], status: Int, msg: String) = {
    responder match {
      case Some(resp) =>
        val success = resp.offerResponse(Message.fromString(Map("status" -> ByteArray(status.toString.getBytes)), msg))
        if (!success)
          responseOverflow.incrementAndGet()
      case None =>
        messageErrors.incrementAndGet()
    }
  }

  val attr503 = Map("status" -> ByteArray(503.toString.getBytes))

  def responseLoop() = {
    while (!closed) {
      responseQueue.poll(200, TimeUnit.MILLISECONDS) match {
        case (incomingResponse, resp) =>
          try {
            val responseSuccess = incomingResponse.result match {
              case Success(msg) => resp.offerResponse(msg)
              case Failure(exception) => resp.offerResponse(Message.fromString(attr503, exception.getMessage))
            }
            if (!responseSuccess)
              responseOverflow.incrementAndGet()
            try {
              if (resp.connection.flow(resp.serverFlowId).isSaturated) {
                val backendFlow = incomingResponse.clientFlow
                if (backendFlow.closeFlow())
                  logger.debug("Closing flow: " + backendFlow)
                closedFlows.put(FlowAssociation(resp.connectionHandler, resp.serverFlowId, backendFlow), true)
              }
            } catch {
              case e: IllegalStateException => // flow expired
              case e: InvalidServerConnectionException => // connection expired
            }
          } catch {
            case NonFatal(e) =>
              sendErrorIfPossible(resp, 500, e.getMessage)
              logger.debug("Error", e)
          }
        case null => // pass
      }
    }
  }

  def sendErrorIfPossible(resp: mot.Responder, status: Int, msg: String) = {
    try {
      respondError(Some(resp), status, msg)
    } catch {
      case e: ResponseAlreadySendException => // can happen
      case NonFatal(e) => logger.error("Could not send error: " + msg)
    }
  }

  def flowLoop() = {
    while (!closed) {
      val it = closedFlows.keySet.iterator
      while (it.hasNext) {
        val FlowAssociation(handler, frontendFlowId, backendFlow) = it.next()
        try {
          if (handler.connection.flow(frontendFlowId).isRecovered) {
            it.remove()
            logger.debug("Opening flow: " + backendFlow)
            backendFlow.openFlow()
          }
        } catch {
          case e: IllegalStateException => it.remove() // flow expired
          case e: InvalidServerConnectionException => it.remove() // connection expired
        }
      }
      Thread.sleep(200)
    }
  }
  
  val flowExpirator = {
    val ex = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory(s"mot-proxy-flow-expirator"))
    val runDelay = Duration(10, TimeUnit.SECONDS)
    ex.scheduleWithFixedDelay(flowExpiratorTask _, runDelay.length, runDelay.length, runDelay.unit)
    ex
  }
  
  val flowGc = Duration(5, TimeUnit.MINUTES)
  
  private def flowExpiratorTask() = {
    try {
      val threshold = System.nanoTime() - flowGc.toNanos
      val it = backendFlows.entrySet.iterator
      while (it.hasNext) {
        val entry = it.next
        val (address, flow) = (entry.getKey, entry.getValue)
        if (flow.lastUse < threshold) {
          logger.debug(s"Expiring flow ${flow.id} after $flowGc of inactivity")
          it.remove()
        }
      }
    } catch {
      case NonFatal(e) => context.uncaughtErrorHandler.handle(e)
    }
  }
  
}