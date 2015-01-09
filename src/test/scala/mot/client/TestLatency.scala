package mot.client

import mot.util.Util.FunctionToRunnable
import mot.Context
import mot.Client
import mot.Address
import mot.Message
import mot.NullPromise
import java.util.concurrent.TimeUnit
import scala.io.StdIn
import mot.util.Promise
import mot.DelayPromise
import mot.util.ByteArray
import org.HdrHistogram.AtomicHistogram
import org.HdrHistogram.Histogram
import java.util.concurrent.atomic.AtomicLong
import org.HdrHistogram.ConcurrentHistogram
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.Lock

object TestLantency extends TestClient {

  def main(args: Array[String]): Unit = {
    val histogram = new ConcurrentHistogram(60000000, 3)
    histogram.setAutoResize(true)
    val (resp, monitoringPort, dumpPort, target, proxyOpt) = parseArgs(args)
    val ctx = new Context(monitoringPort, dumpPort)
    val client = new Client(
      ctx,
      "test-client",
      sendingQueueSize = 200000,
      readerBufferSize = 200000,
      writerBufferSize = 200000)
    val body = "hola, la concha de tu madre"
    val (realTarget, message) = proxyOpt match {
      case Some(proxy) => (proxy, Message.fromString(Map("Proxy" -> ByteArray(target.toString.getBytes)), body))
      case None => (target, Message.fromString(Nil, body))
    }
    @volatile var closed = false
    val counter = new AtomicLong
    val lock = new ReentrantReadWriteLock
    val readLock = lock.readLock()
    val writeLock = lock.writeLock()
    def send() = {
      while (!closed) {
        val start = System.nanoTime()
        val res = client.getResponse(realTarget, message, 15000)
        val time = (System.nanoTime() - start) / 1000
        withLock(readLock)(histogram.recordValue(time))
        val i = counter.incrementAndGet()
        if (i % 50000 == 0) {
          withLock(writeLock) {
            histogram.outputPercentileDistribution(System.out, 1, 1.0)
            histogram.reset()
          }
        }
      }
    }
    val clientThread1 = new Thread(send _, "client-thread-1")
    val clientThread2 = new Thread(send _, "client-thread-2")
    val clientThread3 = new Thread(send _, "client-thread-3")
    val clientThread4 = new Thread(send _, "client-thread-4")
    clientThread1.start()
    clientThread2.start()
    clientThread3.start()
    clientThread4.start()
    Console.println("Press return to exit")
    StdIn.readLine()
    ctx.close()
    closed = true
    clientThread1.join()
    clientThread2.join()
    clientThread3.join()
    clientThread4.join()
  }

  def withLock[A](lock: Lock)(thunk: => A) = {
    lock.lock()
    try {
      thunk
    } finally {
      lock.unlock()
    }
  }

}