package mot.util

import java.util.concurrent.atomic.AtomicLong

object Util {

  implicit class CeilingDivider(val n: Long) extends AnyVal {
    def /^(d: Long) = (n + d - 1) / d
  }
  
  implicit def atomicLong2Getter(al: AtomicLong): () => Long = al.get
  
}