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
import mot.InvalidConnectionException
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
import java.util.concurrent.ThreadPoolExecutor
import mot.util.ExecutorPromise
import mot.ServerFlow

/**
 * Mot reverse proxy
 */
object Proxy {

  def main(args: Array[String]) {
    val context = new Context(monitoringPort = args(0).toInt, dumpPort = args(1).toInt)
    val proxy = new Proxy(context)
    proxy.run()
  }

}

class Proxy(val context: Context) extends StrictLogging {

  val name = "proxy"

  val frontendExecutor = new ThreadPoolExecutor(
    2, 2, Long.MaxValue, TimeUnit.MILLISECONDS, new LinkedBlockingQueue[Runnable](10000), new ThreadPoolExecutor.CallerRunsPolicy)

  val frontend = new Server(
    context,
    name,
    frontendExecutor,
    requestHandler,
    6000,
    maxQueueSize = 100000,
    readBufferSize = 200000,
    writeBufferSize = 200000)

  val backendExecutor = new ThreadPoolExecutor(
    1, 1, Long.MaxValue, TimeUnit.MILLISECONDS, new LinkedBlockingQueue[Runnable](10000), new ThreadPoolExecutor.CallerRunsPolicy)

  val backend = new Client(
    context,
    name,
    maxQueueSize = 100000,
    readBufferSize = 200000,
    writeBufferSize = 200000)

  val responseQueue = new LinkedBlockingQueue[(IncomingResponse, Responder)](100000)
  val backendFlows = new ConcurrentHashMap[ServerFlow, ClientFlow]()
  val closedFlows = new ConcurrentHashMap[ServerFlow, ClientFlow]()

  val responseOverflow = new AtomicLong
  val messageErrors = new AtomicLong

  @volatile var closed = false

  def run() {
    val flowMonitorThread = new Thread(flowLoop _, "closed-flows-monitor")
    flowMonitorThread.start()
    Console.println("Press return to exit")
    StdIn.readLine()
    context.close()
    closed = true
    frontendExecutor.shutdown()
    backendExecutor.shutdown()
    flowExpirator.shutdown()
    frontendExecutor.awaitTermination(Long.MaxValue, TimeUnit.MILLISECONDS)
    backendExecutor.awaitTermination(Long.MaxValue, TimeUnit.MILLISECONDS)
    flowExpirator.awaitTermination(Long.MaxValue, TimeUnit.MILLISECONDS)
    flowMonitorThread.join()
  }

  def getFlow(frontEndFlow: ServerFlow) = {
    val existent = backendFlows.get(frontEndFlow)
    if (existent != null) {
      existent
    } else {
      synchronized {
        val flow = backend.createFlow()
        backendFlows.put(frontEndFlow, flow)
        flow
      }
    }
  }

  class RequestError(msg: String) extends Exception(msg)

  def requestHandler(msg: IncomingMessage): Unit = try {
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
        val promise = new ExecutorPromise(backendExecutor, responseHandler(resp))
        val flow = getFlow(resp.flow)
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

  def respondError(responder: Option[Responder], status: Int, msg: String) = {
    responder match {
      case Some(responder) =>
        val success = responder.offer(Message.fromString(Map("status" -> ByteArray(status.toString.getBytes)), msg))
        if (!success)
          responseOverflow.incrementAndGet()
      case None =>
        messageErrors.incrementAndGet()
    }
  }

  val attr503 = Map("status" -> ByteArray(503.toString.getBytes))

  def responseHandler(responder: Responder)(incomingResponse: IncomingResponse): Unit = try {
    val responseSuccess = incomingResponse.message match {
      case Success(msg) => responder.offer(msg)
      case Failure(exception) => responder.offer(Message.fromString(attr503, exception.getMessage))
    }
    if (!responseSuccess)
      responseOverflow.incrementAndGet()
    try {
      val shouldCloseBackEnd = responder.flow.isSaturated
      if (shouldCloseBackEnd) {
        val backendFlow = incomingResponse.clientFlow
        if (backendFlow.closeFlow())
          logger.debug("Closing flow: " + backendFlow)
        closedFlows.put(responder.flow, backendFlow)
      }
    } catch {
      case e: InvalidConnectionException => // connection expired
    }
  } catch {
    case NonFatal(e) =>
      sendErrorIfPossible(responder, 500, e.getMessage)
      logger.debug("Error", e)
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
      val it = closedFlows.iterator
      while (it.hasNext) {
        val (frontEndFlow, backendFlow) = it.next()
        try {
          if (frontEndFlow.isTerminated || frontEndFlow.isRecovered) {
            it.remove()
            logger.debug("Opening flow: " + backendFlow)
            backendFlow.openFlow()
          }
        } catch {
          case e: InvalidConnectionException => it.remove() // connection expired
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

  private def flowExpiratorTask() = {
    try {
      val it = backendFlows.entrySet.iterator
      while (it.hasNext) {
        val entry = it.next
        val frontEndFlow = entry.getKey
        if (frontEndFlow.isTerminated)
          it.remove()
      }
    } catch {
      case NonFatal(e) => context.uncaughtErrorHandler.handle(e)
    }
  }

}