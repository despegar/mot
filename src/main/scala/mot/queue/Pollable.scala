package mot.queue

import java.util.concurrent.TimeUnit

/**
 * This trait captures the "head side" of the {@code BlockingQueue} interface
 */
trait Pollable[+T] {

  /**
   * Retrieve and removes the head of this queue, waiting up to the specified wait time if necessary for an element to 
   * become available.
   *
   * @param timeout how long to wait before giving up, in units of <tt>unit</tt>
   * @param unit a <tt>TimeUnit</tt> determining how to interpret the <tt>timeout</tt> parameter
   * @return the head of this queue, or <tt>null</tt> if the specified waiting time elapses before an element is 
   *     available
   * @throws InterruptedException if interrupted while waiting
   */
  def poll(timeout: Long, TimeUnit: TimeUnit): T

  def isEmpty(): Boolean
}