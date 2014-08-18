package mot

import java.util.concurrent.ConcurrentHashMap
import mot.monitoring.Commands

// TODO: Make class
object Context {

  val clients = new ConcurrentHashMap[String, Client]
  
  new Commands().start()
  
}