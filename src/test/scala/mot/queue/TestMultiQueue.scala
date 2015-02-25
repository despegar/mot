package mot.queue

import mot.util.Util.FunctionToRunnable
import java.util.concurrent.TimeUnit

object TestMultiQueue {

  def main(args: Array[String]) {
    val queue = new LinkedBlockingMultiQueue[Int, String](100)
    val p1 = queue.addSubQueue(1, priority = 1)
    val p2 = queue.addSubQueue(2, priority = 1)
    val p3 = queue.addSubQueue(3, priority = 0)
    def producer1() {
      while (true) {
        p1.offer("1", 500, TimeUnit.SECONDS)
      }
    }
    def producer2() {
      while (true) {
        p2.offer("2", 500, TimeUnit.SECONDS)
      }
    }    
    def producer3() {
      while (true) {
        p3.offer("3", 500, TimeUnit.SECONDS)
        Thread.sleep(200)
      }
    }
    def consumer() {
      var i = 0
      while (true) {
        val x = queue.poll(500, TimeUnit.SECONDS)
        if (x != null)
          Console.println(x)
        Thread.sleep(100)
        if (i % 30 == 0)
          Console.println(s"p1:${p1.size},p2:${p2.size},p3:${p3.size}")
        i += 1
      }
    }
    val thread1 = new Thread(producer1 _, "thread-1").start()
    val thread2 = new Thread(producer2 _, "thread-2").start()
    val thread3 = new Thread(producer3 _, "thread-3").start()
    val threadConsumer = new Thread(consumer _, "thread-consumer").start()
    Thread.sleep(3000)
    p1.enable(false)
    Thread.sleep(3000)
    p2.enable(false)
    Thread.sleep(3000)
    p2.enable(true)
    Thread.sleep(3000)
    p1.enable(true)
  }

}