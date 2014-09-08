package mot.monitoring

import scala.collection.immutable

trait SimpleCommandHandler extends CommandHandler {
  
  def handle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String], partWriter: String => Unit) = {
    simpleHandle(processedCommands, commands)
  }
  
  def simpleHandle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String]): String
  
}