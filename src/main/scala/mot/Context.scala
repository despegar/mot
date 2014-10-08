package mot

import java.util.concurrent.ConcurrentHashMap

import mot.monitoring.Commands

/**
 * Mot context. Clients and servers need a context.
 * 
 * @param monitoringPort port to bind the monitoring socket that the 'motstat' utility uses.
 * @param uncaughtErrorHandler a handler for unexpected error (bugs).
 */
class Context(val monitoringPort: Int = 4001, val uncaughtErrorHandler: UncaughtErrorHandler = LoggingErrorHandler) {

  private[mot] val clients = new ConcurrentHashMap[String, Client]
  private[mot] val servers = new ConcurrentHashMap[String, Server]
  
  new Commands(this, monitoringPort).start()
 
  private[mot] val dumper = new Dumper(6000)
  
  dumper.start()
  
  def registerClient(client: Client) = {
    val old = clients.putIfAbsent(client.name, client)
    if (old != null)
      throw new Exception(s"A client with name ${client.name} is already registered.")
  }

  def registerServer(server: Server) = {
    val old = servers.putIfAbsent(server.name, server)
    if (old != null)
      throw new Exception(s"A server with name ${server.name} is already registered.")
  }
  
  // TODO: Close and unregisters

}