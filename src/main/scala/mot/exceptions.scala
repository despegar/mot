package mot

// Common

/* Useful to speed up exceptions that are not thrown at creation point */
trait NoStackTraceException extends Exception {
  override def fillInStackTrace() = this
}

class BadDataException(msg: String) extends Exception(msg)

class UncompatibleProtocolVersion(message: String) extends Exception(message)

class MessageTooLargeException(size: Int, maxSize: Int) 
  extends Exception(s"Message is bigger than the maximum allowed by the other party ($size > $maxSize)")

// Client

class ResponseTimeoutException extends Exception("Response timed out.") with NoStackTraceException

class ClientClosedException extends Exception

class ErrorStateException(cause: Throwable) 
  extends Exception("Cannot send message because the connector is in error state and the client is configured as pessimistic", cause)

class InvalidClientConnectionException(cause: Throwable) 
  extends Exception("Cannot get response because client the connection was terminated", cause)

// Server

class ServerClosedException extends Exception

class TooLateException(val delay: Long) extends Exception(s"Cannot send respond because request timed out. Delay: $delay ns")

class ResponseAlreadySendException extends Exception("Message has already been responded")

class InvalidServerConnectionException(cause: Throwable)
  extends Exception("Cannot send response because server the connection from which the request came was terminated", cause)

class MessageNotRespondableException extends Exception("Message cannot be responded")
