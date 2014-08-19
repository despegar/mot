package mot

import java.util.concurrent.ConcurrentHashMap
import mot.monitoring.Commands

// TODO: Make class
object Context {

  val clients = new ConcurrentHashMap[String, Client]
  val servers = new ConcurrentHashMap[String, Server]
  
  new Commands().start()
  
}