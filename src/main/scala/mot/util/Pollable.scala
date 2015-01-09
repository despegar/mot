package mot.util

import java.util.concurrent.TimeUnit

trait Pollable[+T] {
  def poll(timeout: Long, TimeUnit: TimeUnit): T
  def isEmpty(): Boolean
}