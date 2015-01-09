package mot

import java.util.concurrent.ConcurrentHashMap
import mot.monitoring.Commands
import mot.dump.Dumper
import scala.collection.JavaConversions._

/**
 * Mot context. Clients and servers need a context.
 * 
 * @param monitoringPort port to bind the monitoring socket that the 'motstat' utility uses.
 * @param uncaughtErrorHandler a handler for unexpected error (bugs).
 */
class Context(
    val monitoringPort: Int = 4001, 
    val dumpPort: Int = 6001,
    val uncaughtErrorHandler: UncaughtErrorHandler = LoggingErrorHandler) {

  private[mot] val clients = new ConcurrentHashMap[String, Client]
  private[mot] val servers = new ConcurrentHashMap[String, Server]
  
  private[mot] val commands = new Commands(this, monitoringPort)
  private[mot] val dumper = new Dumper(dumpPort)
  
  commands.start()
  dumper.start()
  
  @volatile private var closed = false
  
  def registerClient(client: Client): Unit = {
    if (closed)
      throw new IllegalStateException("Context already closed")
    val old = clients.putIfAbsent(client.name, client)
    if (old != null)
      throw new Exception(s"A client with name ${client.name} is already registered.")
  }

  def registerServer(server: Server): Unit = {
    if (closed)
      throw new IllegalStateException("Context already closed")
    val old = servers.putIfAbsent(server.name, server)
    if (old != null)
      throw new Exception(s"A server with name ${server.name} is already registered.")
  }
  
  def close() = {
    closed = true
    clients.values.foreach(_.close())
    servers.values.foreach(_.close())
    dumper.stop()
    commands.stop()
  }

}