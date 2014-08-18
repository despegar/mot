package mot.monitoring

trait SimpleCommandHandler extends CommandHandler {
  
  def handle(processedCommands: Seq[String], commands: Seq[String], partWriter: String => Unit) = {
    simpleHandle(processedCommands, commands)
  }
  
  def simpleHandle(processedCommands: Seq[String], commands: Seq[String]): String
  
}