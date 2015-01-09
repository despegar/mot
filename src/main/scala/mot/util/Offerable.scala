package mot.util

import java.util.concurrent.TimeUnit

trait Offerable[-T] {
  def offer(e: T, timeout: Long, TimeUnit: TimeUnit): Boolean
  def offer(e: T): Boolean
  def size(): Int
  def capacity(): Int
  def isEnabled(): Boolean
  def enable(status: Boolean): Unit
}
