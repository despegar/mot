package mot

import java.util.concurrent.ConcurrentHashMap
import mot.monitoring.Commands

class Context(val monitoringPort: Int = 4001, val uncaughtErrorHandler: UncaughtErrorHandler = LoggingErrorHandler) {

  val clients = new ConcurrentHashMap[String, Client]
  val servers = new ConcurrentHashMap[String, Server]
  
  new Commands(this, monitoringPort).start()
 
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

}