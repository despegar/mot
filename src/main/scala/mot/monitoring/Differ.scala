package mot.monitoring

import java.util.concurrent.atomic.AtomicLong

class Differ(val counter: AtomicLong) {

  var previous = counter.get()

  def diff() = {
    val newValue = counter.get()
    val diff = newValue - previous // also correct in case of counter overflow
    previous = newValue
    diff
  }

}
