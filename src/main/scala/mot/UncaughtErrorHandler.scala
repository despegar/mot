package mot

trait UncaughtErrorHandler {
  def handle(throwable: Throwable)
}