package mot.util

/**
 * Exceptions can mix this trait to avoid recording the stack trace, which is time consuming. In particular, the stack
 * offers not very useful (and even confusing) information in the cases when the exception is not thrown at the point
 * of creation, but afterwards (when the stack will be different anyway). 
 */
trait NoStackTraceException extends Exception {
  override def fillInStackTrace() = this
  def doFillStackTrace() = super.fillInStackTrace()
}