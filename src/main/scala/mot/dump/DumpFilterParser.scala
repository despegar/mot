package mot.dump

import scala.util.parsing.combinator.syntactical.StandardTokenParsers
import scala.util.parsing.combinator.lexical.StdLexical
import mot.protocol.MessageTypes

class DumpFilterParser extends StandardTokenParsers {

  import Filters._
  
  override val lexical = new StdLexical {
    override def identChar = 
      letter | elem('_') | elem('-') | elem('.') | elem('*') | elem('+') | elem('?') | elem(':')
  }
  
  lexical.reserved ++= 
    Seq("type", "or", "and", "not", "len", "incoming", "outgoing", "attr", "src", "dst", "host", "port")
    
  lexical.delimiters ++= Seq("(", ")", "||", "&&", "!", "<", ">", "<=", ">=", "==", "~=", "[", "]")
  
  def filter: Parser[Filter] = branch ~ (or ~> filter).? ^^ {
    case left ~ Some(right) => Disj(left, right)
    case unique ~ None => unique
  }

  def branch: Parser[Filter] = atom ~ (and ~> branch).? ^^ {
    case left ~ Some(right) => Conj(left, right)
    case unique ~ None => unique
  }

  def atom = not.? ~ expr ^^ {
    case Some(_) ~ expr => Neg(expr)
    case None ~ expr => expr
  }

  def or = "or" | "||"
  def and = "and" | "&&"
  def not = "not" | "!"
  
  def expr = 
    messageType | port | host | direction | lengthFilter | attrValue | attrRegex | attrPresence | group | 
    failure("illegal expression")
  
  def group = "(" ~> filter <~ ")"

  def messageType = "type" ~> ident ^? (
    { case name if MessageTypes.isValid(name) => Type(MessageTypes.names(name)) },
    { name => s"invalid message type '$name', legal values are: ${MessageTypes.names.keys.mkString(",")}" }
  )
  
  def port = sideFilter.? ~ ("port" ~> numericLit) ^^ {
    case Some(sideFilter) ~ number => Port(sideFilter, number.toInt)
    case None ~ number => Port(Side.Any, number.toInt)
  }
  
  def host = sideFilter.? ~ ("host" ~> (ident | stringLit)) ^^ {
    case Some(sideFilter) ~ host => Host(sideFilter, host)
    case None ~ host => Host(Side.Any, host)
  }
  
  def direction = incoming | outgoing
  
  def incoming = "incoming" ^^^ Dir(Direction.Incoming)
  def outgoing = "outgoing" ^^^ Dir(Direction.Outgoing)
  
  def sideFilter = srcFilter | destFilter | srcOrDestFilter
  
  def srcFilter = "src" ^^^ Side.Source
  def destFilter = "dst" ^^^ Side.Dest
  def srcOrDestFilter = "src" ~ "or" ~ "dst" ^^^ Side.Any
  
  def lengthFilter = lengthLess | lengthGreater | lengthLessEqual | lengthGreaterEqual
  
  def lengthLess = "len" ~> "<" ~> numericLit ^^ (len => LengthLess(len.toInt))
  def lengthGreater = "len" ~> ">" ~> numericLit ^^ (len => LengthGreater(len.toInt))
  def lengthLessEqual = "len" ~> "<=" ~> numericLit ^^ (len => LengthLessEqual(len.toInt))
  def lengthGreaterEqual = "len" ~> ">=" ~> numericLit ^^ (len => LengthGreaterEqual(len.toInt))
  
  def attrPresence = "attr" ~> "[" ~> ident <~ "]" ^^ (AttributePresence(_))
  
  def attrValue = ("attr" ~> "[" ~> (ident | stringLit) <~ "]") ~ ("==" ~> (ident | stringLit)) ^^ {
    case name ~ value => AttributeValue(name, value)
  }
  
  def attrRegex = ("attr" ~> "[" ~> (ident | stringLit) <~ "]") ~ ("~=" ~> (ident | stringLit)) ^^ {
    case name ~ regex => AttributeRegex(name, regex)
  }
  
  def parseAll(source: String): ParseResult[Filter] = {
    val tokens = new lexical.Scanner(source)
    phrase(filter)(tokens)
  }
  
}