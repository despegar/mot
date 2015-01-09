package mot.dump

import scala.util.parsing.combinator.syntactical.StandardTokenParsers
import scala.util.parsing.combinator.lexical.StdLexical
import mot.protocol.MessageTypes

class DumpFilterParser extends StandardTokenParsers {

  override val lexical = new StdLexical {
    override def identChar = 
      letter | elem('_') | elem('-') | elem('.') | elem('*') | elem('+') | elem('?') | elem(':')
  }
  
  lexical.reserved ++= 
    Seq("type", "or", "and", "not", "len", "incoming", "outgoing", "attr", "src", "dst", "host", "port")
    
  lexical.delimiters ++= Seq("(", ")", "||", "&&", "!", "<", ">", "<=", ">=", "==", "~=", "[", "]")
  
  def filter: Parser[Filter] = branch ~ (or ~> filter).? ^^ {
    case left ~ Some(right) => Disjunction(left, right)
    case unique ~ None => unique
  }

  def branch: Parser[Filter] = atom ~ (and ~> branch).? ^^ {
    case left ~ Some(right) => Conjunction(left, right)
    case unique ~ None => unique
  }

  def atom = not.? ~ expr ^^ {
    case Some(_) ~ expr => Negation(expr)
    case None ~ expr => expr
  }

  def or = "or" | "||"
  def and = "and" | "&&"
  def not = "not" | "!"
  
  def expr = 
    (messageType | port | host | direction | lengthFilter | attrValue | attrRegex | attrPresence | group).
      withFailureMessage("illegal start of expression")
  
  def group = "(" ~> filter <~ ")"

  def messageType = "type" ~> ident ^? (
    { case name if MessageTypes.isValid(name) => MessageTypeFilter(MessageTypes.names(name)) },
    name => s"invalid message type '$name', legal values are: ${MessageTypes.names.keys.mkString(",")}"
  )
  
  def port = sideFilter.? ~ ("port" ~> numericLit) ^^ {
    case Some(sideFilter) ~ number => Port(sideFilter, number.toInt)
    case None ~ number => Port(Side.SourceOrDest, number.toInt)
  }
  
  def host = sideFilter.? ~ ("host" ~> (ident | stringLit)) ^^ {
    case Some(sideFilter) ~ host => Host(sideFilter, host)
    case None ~ host => Host(Side.SourceOrDest, host)
  }
  
  def direction = incoming | outgoing
  
  def incoming = "incoming" ^^^ DirectionFilter(Direction.Incoming)
  def outgoing = "outgoing" ^^^ DirectionFilter(Direction.Outgoing)
  
  def sideFilter = srcFilter | destFilter | srcOrDestFilter
  
  def srcFilter = "src" ^^^ Side.Source
  def destFilter = "dst" ^^^ Side.Dest
  def srcOrDestFilter = "src" ~ "or" ~ "dst" ^^^ Side.SourceOrDest
  
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