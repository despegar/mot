package mot.monitoring

import scala.collection.immutable

trait CommandHandler {

  /**
   * Handle a command. This method can either return a String, which is returned immediately to the user,
   * or call the partWriter function as many times as it wants. The latter mode streams lines to the client.
   * After the first partWriter call, the implementation must throw an exception to finish the command.
   */
  def handle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String], partWriter: String => Unit): String

  def name: String
  
}

