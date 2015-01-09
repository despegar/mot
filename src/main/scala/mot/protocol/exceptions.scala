package mot.protocol

trait ProtocolException extends Exception

trait ProtocolSyntaxException extends ProtocolException

object ProtocolSyntaxException {
  def apply(msg: String) = new Exception(msg) with ProtocolSyntaxException
  def apply(msg: String, cause: Throwable) = new Exception(msg, cause) with ProtocolSyntaxException
}

class ProtocolSemanticException(msg: String) extends Exception(msg) with ProtocolException
