package mot.monitoring

import java.util.concurrent.atomic.AtomicLong

class Differ private (val counter: () => Long) {

  var previous = counter()

  def diff() = {
    val newValue = counter()
    val diff = newValue - previous // also correct in case of counter overflow
    previous = newValue
    diff
  }

}

object Differ {
  def fromVolatile(volatile: () => Long) = new Differ(volatile)
  def fromAtomic(atomic: AtomicLong) = new Differ(atomic.get)
}