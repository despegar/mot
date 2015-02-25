package mot.queue

import java.util.concurrent.TimeUnit

/**
 * This trait captures the "tail side" of the {@code BlockingQueue} interface
 */
trait Offerable[-T] {

  /**
   * Insert the specified element into this queue, waiting up to the specified wait time if necessary for space to 
   * become available.
   *
   * @param e the element to add
   * @param timeout how long to wait before giving up, in units of <tt>unit</tt>
   * @param unit a <tt>TimeUnit</tt> determining how to interpret the <tt>timeout</tt> parameter
   * @return <tt>true</tt> if successful, or <tt>false</tt> if the specified waiting time elapses before space is 
   *     available
   */
  def offer(e: T, timeout: Long, TimeUnit: TimeUnit): Boolean

  /**
   * Insert the specified element into this queue if it is possible to do so immediately without violating capacity
   * restrictions, returning <tt>true</tt> upon success and <tt>false</tt> if no space is currently available.
   *
   * @param e the element to add
   * @return <tt>true</tt> if the element was added to this queue, else <tt>false</tt>
   */
  def offer(e: T): Boolean

}
