package mot

/* Useful to speed up exceptions that are not thrown at creation point */
trait NoStackTraceException extends Exception {
  override def fillInStackTrace() = this
}

// Common

class MessageTooLargeException(size: Int, maxSize: Int) 
  extends Exception(s"message is bigger than the maximum allowed by the other party ($size > $maxSize)")

class CounterpartyClosedException(msg: String) extends Exception(msg)

class ByeException extends CounterpartyClosedException("bye frame received from counterparty")

class ResetException(msg: String) extends CounterpartyClosedException(msg)

class GreetingAbortedException extends Exception

class LocalClosedException extends Exception("connector closed locally")

class InvalidConnectionException(cause: Throwable) extends Exception("the connection was terminated", cause)

// Client

class ResponseTimeoutException extends Exception("Response timed out") with NoStackTraceException

class ErrorStateException(cause: Throwable) 
  extends Exception(
      "cannot send message because the connector is in error state and the client is configured as pessimistic", cause)

class ResponseAlreadySendException extends Exception("message has already been responded")

class NotRespondableException extends Exception("message cannot be responded")
