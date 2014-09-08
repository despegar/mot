package mot.monitoring

import scala.collection.immutable

trait Alternatives {
  val map: Map[String, Alternatives] = Map()
  override def toString() = map.toString
}

trait MultiCommandHandler extends CommandHandler {

  def handle(processedCommands: immutable.Seq[String], commands: immutable.Seq[String], partWriter: String => Unit) = {
    commands match {
      case head +: tail =>
        subcommands.find(_.name == head) match {
          case Some(subcommand) =>
            subcommand.handle(processedCommands :+ head, tail, partWriter)
          case None =>
            showHelp(processedCommands, immutable.Seq(), error = Some(s"Invalid option: '$head'"))
        }
      case Seq() =>
        showHelp(processedCommands, immutable.Seq())
    }
  }

  def subcommands: immutable.Seq[CommandHandler]

  def showHelp(processed: immutable.Seq[String], commands: immutable.Seq[String], error: Option[String] = None) = {
    s"${error.map(_ + "\n").getOrElse("")}" +
      s"$helpLine\n" +
      s"Usage: ${processed.mkString(" ")} ${subcommands.map(_.name).mkString("{", "|", "}")}"
  }

  def commandAlternatives: Alternatives = new Alternatives {
    override val map = {
      val res= subcommands.map { handler =>
        val value = handler match {
          case x: MultiCommandHandler => x.commandAlternatives
          case _ => new Alternatives {}
        }
        handler.name -> value
      } 
      res.toMap
    }
  }

  def helpLine: String

}