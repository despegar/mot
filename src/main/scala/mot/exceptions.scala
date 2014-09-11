package mot

// Client

class ResponseTimeoutException extends Exception("Response timed out.")

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

// Common

class BadDataException(msg: String) extends Exception(msg)

class UncompatibleProtocolVersion(message: String) extends Exception(message)

