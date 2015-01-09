package mot.util

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ThreadFactory

class NamedThreadFactory(name: String) extends ThreadFactory {
  
  private val idx = new AtomicInteger
  
  def newThread(r: Runnable) = {
    val i = idx.getAndIncrement()
    val threadName = if (i == 0) name else name + "-" + i
    new Thread(r, threadName)
  }
  
}