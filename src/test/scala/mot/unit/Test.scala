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
import mot.dump.MotEvent
import mot.IncomingMessage
import java.util.concurrent.Executors

class Test extends FunSuite with BeforeAndAfterAll {

  val port = 5000

  val target = Address("localhost", port)
  val invalidTarget = Address("localhost", port + 1)

  val timeoutMs = 6000
  val pollMs = 5000

  val iterations = 1000

  test(s"(request + response) * $iterations") {
    val ctx = new Context
    val client = new Client(ctx, "client")
    val request = Message.fromString("the-request")
    val response = Message.fromString("the-response")
    def handler(incomingMessage: IncomingMessage): Unit = {
      assertResult(request.stringBody)(incomingMessage.message.stringBody)
      val responseSuccess = incomingMessage.responder.offer(response)
      assert(responseSuccess)
    }
    val server = new Server(ctx, "server", Executors.newSingleThreadExecutor, handler, bindPort = port)
    for (i <- 1 to iterations) {
      val promise = new UnaryPromise[IncomingResponse]
      val requestSuccess = client.offerRequest(target, request, timeoutMs, promise)
      assert(requestSuccess)
      val incominResponse = promise.result(pollMs, TimeUnit.MILLISECONDS).get
      val receivedResponse = incominResponse.message.get
      assertResult(response.stringBody)(receivedResponse.stringBody)
    }
    ctx.close()
  }

  test(s"invalid request with pessimistic error") {
    val ctx = new Context
    val pessimisticClient = new Client(ctx, "pessimisic-client", tolerance = Duration(100, TimeUnit.MILLISECONDS))
    val request = Message.fromString("the-request")
    val firstSuccess = pessimisticClient.offerRequest(invalidTarget, request, timeoutMs, new UnaryPromise[IncomingResponse])
    assert(firstSuccess)
    Thread.sleep(200)
    intercept[Exception] {
      pessimisticClient.offerRequest(invalidTarget, request, timeoutMs, new UnaryPromise[IncomingResponse])
    }
    pessimisticClient.close()
    ctx.close()
  }

  test("dump") {
    val ctx = new Context(monitoringPort = 4002, dumpPort = 6002)
    val listener = ctx.dumper.listen(bufferSize = 1000)
    val client = new Client(ctx, "client-dump")
    def handle(incomingMessage: IncomingMessage): Unit = {
      assert(incomingMessage != null)
      val response = Message.fromString("the-response")
      val responseSuccess = incomingMessage.responder.offer(response)
      assert(responseSuccess)
    }
    val server = new Server(ctx, "server-dump", Executors.newSingleThreadExecutor, handle, bindPort = port + 2)
    val target = Address("localhost", port + 2)
    val request = Message.fromString("the-request")
    val promise = new UnaryPromise[IncomingResponse]
    val requestSuccess = client.offerRequest(target, request, timeoutMs, promise)
    assert(requestSuccess)
    val receivedResponse = promise.result(pollMs, TimeUnit.MILLISECONDS).get.message.get
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
    val incomingMessages = for (MotEvent(conn, dir, msg) <- incoming) yield msg
    val outgoingMessages = for (MotEvent(conn, dir, msg) <- outgoing) yield msg
    // transform into sets because hello frames can appear in different order in each side or the connection
    assert(incomingMessages.toSet == outgoingMessages.toSet)
  }

}