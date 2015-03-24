package mot.queue

import java.util.concurrent.TimeUnit

/**
 * This trait captures the "head side" of the {@code BlockingQueue} interface
 */
trait Pollable[+T] {

  /**
   * Retrieve and remove the head of this queue, waiting up to the specified wait time if necessary for an element to 
   * become available.
   *
   * @param timeout how long to wait before giving up, in units of <tt>unit</tt>
   * @param unit a <tt>TimeUnit</tt> determining how to interpret the <tt>timeout</tt> parameter
   * @return the head of this queue, or <tt>null</tt> if the specified waiting time elapses before an element is 
   *     available
   */
  def poll(timeout: Long, TimeUnit: TimeUnit): T
  
  /**
   * Retrieve and remove the head of this queue, waiting up to the specified wait time if necessary for an element to 
   * become available. Also retrieve the remaining quantity of elements. This value can be used to predict whether
   * a following poll would block (if there is a single consumer).
   *
   * @param timeout how long to wait before giving up, in units of <tt>unit</tt>
   * @param unit a <tt>TimeUnit</tt> determining how to interpret the <tt>timeout</tt> parameter
   * @return a pair, consisting of the head of this queue, or <tt>null</tt> if the specified waiting time elapses 
   *     before an element is available, and the remaining quantity of elements after the returned element (if any)
   *     was removed.
   */
  def pollWithRemaining(timeout: Long, TimeUnit: TimeUnit): (T, Int)

  def isEmpty(): Boolean
  
}