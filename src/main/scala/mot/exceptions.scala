package mot

import mot.util.NoStackTraceException

// Common

/**
 * Exception thrown when trying to send a message bigger than the counter party would accept.
 */
class MessageTooLargeException(size: Int, maxSize: Int) 
  extends Exception(s"message is bigger than the maximum allowed by the other party ($size > $maxSize)")

/**
 * Exception that mix this trait are used as a cause when a connection was terminated (somehow) by the counter party.
 */
trait CounterpartyClosedException extends Exception

/**
 * Exception used as a cause when a connection was normally terminated by the counter party.
 */
class ByeException extends Exception("bye frame received from counterparty") with CounterpartyClosedException

/**
 * Exception used as a cause when a connection was abnormally terminated by the counter party.
 */
class ResetException(msg: String) extends Exception(msg) with CounterpartyClosedException

/**
 * Exception use as a cause to signal the cases when the client or server was locally closed.
 */
class LocalClosedException extends Exception("connector closed locally")

// Server

/**
 * Exception thrown by the server when trying to responde a message over an already closed connection.
 */
class InvalidConnectionException(cause: Throwable) extends Exception("the connection was terminated", cause)

// Client

/**
 * Exception use as a cause to signal the cases when the response of a message timed out.
 */
class ResponseTimeoutException extends Exception("Response timed out") with NoStackTraceException

/**
 * Exception thrown when the client could not establish a connection and the tolerance period is exceeded. 
 */
class ErrorStateException(cause: Throwable) 
  extends Exception("cannot send message because the connector is in error state and tolerance was exceeded", cause)

/**
 * Exception thrown when trying to response the same message twice.
 */
class ResponseAlreadySendException extends Exception("message has already been responded")

/**
 * Exception thrown when trying to response an unrespondable message.
 */
class NotRespondableException extends Exception("message cannot be responded")
