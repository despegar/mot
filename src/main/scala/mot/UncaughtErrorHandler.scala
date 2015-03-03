package mot

/**
 * A trait containing a method to be called in the case of a fatal error in the Mot implementation. This errors would
 * be in fact bugs and will not happen, in theory. In case they happen in practice, this trait is used to define what 
 * to do. Reasonable behaviors are logging with an error level or (if appropiate) crashing the process.
 */
trait UncaughtErrorHandler {
  def handle(throwable: Throwable): Unit
}