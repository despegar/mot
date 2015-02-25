package mot.util

class Differ(val counter: () => Long) {

  var previous = counter()

  def diff() = {
    val newValue = counter()
    val diff = newValue - previous // also correct in case of counter overflow
    previous = newValue
    diff
  }

}
