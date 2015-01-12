package mot.unit

import org.scalatest.FunSuite
import mot.Context
import mot.Client
import mot.Server
import mot.Address
import mot.util.UnaryPromise
import mot.IncomingResponse
import mot.Message
import java.util.concurrent.TimeUnit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.ConfigMap
import scala.concurrent.duration.Duration
import java.text.SimpleDateFormat
import scala.collection.mutable.ListBuffer
import mot.dump.Event
import mot.dump.Direction
import mot.dump.MessageEvent

class Test extends FunSuite with BeforeAndAfterAll {

  val port = 5000
  val ctx = new Context
  val client = new Client(ctx, "client")
  val server = new Server(ctx, "server", bindPort = port)

  val target = Address("localhost", port)
  val invalidTarget = Address("localhost", port + 1)

  val timeoutMs = 500
  val pollMs = 100

  val iterations = 1000

  test(s"(request + response) * $iterations") {
    for (i <- 1 to iterations) {
      val request = Message.fromString(Nil, "the-request " + i)
      val promise = new UnaryPromise[IncomingResponse]
      val requestSuccess = client.offerRequest(target, request, timeoutMs, promise)
      assert(requestSuccess)
      val incomingMessage = server.poll(pollMs, TimeUnit.MILLISECONDS)
      assertResult(request.stringBody)(incomingMessage.message.stringBody)
      val response = Message.fromString(Nil, "the-response-" + i)
      val responseSuccess = incomingMessage.responder.offer(response)
      assert(responseSuccess)
      val receivedResponse = promise.result(pollMs, TimeUnit.MILLISECONDS).get.result.get
      assertResult(response.stringBody)(receivedResponse.stringBody)
    }
  }

  test(s"request * $iterations + response") {
    val requests = for (i <- 1 to iterations) yield {
      val request = Message.fromString(Nil, "the-request " + i)
      val promise = new UnaryPromise[IncomingResponse]
      val success = client.offerRequest(target, request, timeoutMs, promise)
      assert(success)
      promise -> request
    }
    val responses = for ((promise, request) <- requests) yield {
      val incomingMessage = server.poll(pollMs, TimeUnit.MILLISECONDS)
      assertResult(request.stringBody)(incomingMessage.message.stringBody)
      val response = Message.fromString(Nil, "the-response")
      val success = incomingMessage.responder.offer(response)
      assert(success)
      promise -> response
    }
    for ((promise, response) <- responses) {
      val receivedResponse = promise.result(pollMs, TimeUnit.MILLISECONDS).get.result.get
      assertResult(response.stringBody)(receivedResponse.stringBody)
    }
  }

  test(s"invalid request * ${iterations + 1} (overflow)") {
    for (i <- 1 to iterations) {
      val request = Message.fromString(Nil, "the-request " + i)
      val promise = new UnaryPromise[IncomingResponse]
      val success = client.offerRequest(invalidTarget, request, timeoutMs, promise)
      assert(success)
    }
    val request = Message.fromString(Nil, "the-request-" + (iterations + 1))
    val success = client.offerRequest(invalidTarget, request, timeoutMs, new UnaryPromise[IncomingResponse])
    assert(!success)
  }

  test(s"invalid request with pessimistic error") {
    val pessimisticClient = new Client(ctx, "pessimisic-client", tolerance = Duration(100, TimeUnit.MILLISECONDS))
    val request = Message.fromString(Nil, "the-request")
    val firstSuccess = pessimisticClient.offerRequest(invalidTarget, request, timeoutMs, new UnaryPromise[IncomingResponse])
    assert(firstSuccess)
    Thread.sleep(200)
    intercept[Exception] {
      pessimisticClient.offerRequest(invalidTarget, request, timeoutMs, new UnaryPromise[IncomingResponse])
    }
    pessimisticClient.close()
  }

  test("dump") {
    val ctx = new Context(monitoringPort = 4002, dumpPort = 6002) 
    val listener = ctx.dumper.listen(bufferSize = 1000)
    val client = new Client(ctx, "client-dump")
    val server = new Server(ctx, "server-dump", bindPort = port + 2)
    val target = Address("localhost", port + 2)
    val request = Message.fromString(Nil, "the-request")
    val promise = new UnaryPromise[IncomingResponse]
    val requestSuccess = client.offerRequest(target, request, timeoutMs, promise)
    assert(requestSuccess)
    val incomingMessage = server.poll(pollMs, TimeUnit.MILLISECONDS)
    val response = Message.fromString(Nil, "the-response")
    val responseSuccess = incomingMessage.responder.offer(response)
    assert(responseSuccess)
    val receivedResponse = promise.result(pollMs, TimeUnit.MILLISECONDS).get.result.get
    val sdf = new SimpleDateFormat("HH:mm:ss.SSS'Z'")
    var event = listener.queue.poll()
    client.close()
    server.close()
    val events = ListBuffer[Event]()
    while (event != null) {
      events += event
      event = listener.queue.poll()
    }
    ctx.close()
    val groups = events.groupBy(_.direction)
    val incoming = groups(Direction.Incoming)
    val outgoing = groups(Direction.Outgoing)
    val incomingMessages = for (MessageEvent(conn, dir, msg) <- incoming) yield msg
    val outgoingMessages = for (MessageEvent(conn, dir, msg) <- outgoing) yield msg
    // transform into sets because hello frames can appear in different order in each side or the connection
    assert(incomingMessages.toSet == outgoingMessages.toSet)
  }

  override def afterAll(configMap: ConfigMap) {
    ctx.close()
  }

}