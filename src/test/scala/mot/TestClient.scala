package mot

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

class TestClient {

  def testUnrespondable() = {
    val ctx = new Context(4001)
    val client = new Client(ctx, "test-client", queueSize = 10000)
    val target = Target("localhost", 5000)
    val msg = Message.fromArrays(Map(), "x".getBytes)
    while (true) {
      client.sendMessage(target, msg)
    }
  }
  
  def testRespondable() = {
    val ctx = new Context(4001)
    val client = new Client(ctx, "test-client", queueSize = 10000)
    val target = Target("localhost", 5000)
    val msg = Message.fromArrays(Map(), "x".getBytes)
    var i = 0L 
    while (true) {
      val res = client.sendRequest(target, msg, 60000)
      //Await.result(res, Duration.apply(60, TimeUnit.SECONDS))
    }
  }
  
}