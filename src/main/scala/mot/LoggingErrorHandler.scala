package mot

import com.typesafe.scalalogging.slf4j.StrictLogging

object LoggingErrorHandler extends UncaughtErrorHandler with StrictLogging {

  def handle(throwable: Throwable) = {
    logger.error(
        "Uncaught exception in thread. This is likely a bug, and a bad one. " + 
    	"From now on, all bets are off and the messaging system will not likely function anymore.", throwable)
  }
    
}