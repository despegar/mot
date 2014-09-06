package mot.util

object Util {

  implicit class CeilingDivider(val n: Long) extends AnyVal {
    def /^(d: Long) = (n + d - 1) / d
  }
  
}