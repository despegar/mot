package mot.queue

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import scala.collection.JavaConversions._
import java.util.concurrent.atomic.AtomicInteger
import scala.IndexedSeq
import java.util.concurrent.ConcurrentHashMap

/**
 * An optionally-bounded blocking "multi-queue" based on linked nodes. A multi-queue is actually a set of queues that
 * are connected at the heads and have independent tails (the head of the queue is that element that has been on the
 * queue the longest time. The tail of the queue is that element that has been on the queue the shortest time). New
 * elements are added at the tail of one of the queues, and the queue retrieval operations obtain elements from the
 * head of some of the queues, according to a policy that is described below.
 *
 * This class essentially allows a consumer to efficiently block a single thread on a set of queues, until one becomes
 * available. The special feature is that individual queues can be enabled or disabled. A disabled queue is not
 * considered for polling (in the event that all the queue are disabled, any blocking operation would do so trying to
 * read, as if all the queues were empty). Elements are taken from the set of enabled queues, obeying the established
 * priority (queues with the same priority are served round robin).
 *
 * A disabled queue accepts new elements normally until it reaches the maximum capacity (if any).
 *
 * Individual queues can be address, removed, enabled or disabled at any time, without any concurrency restrictions.
 *
 * The optional capacity bound constructor argument serves as a way to prevent excessive queue expansion. The capacity,
 * if unspecified, is equal to Int.MaxVaue. Linked nodes are dynamically created upon each insertion unless this would
 * bring the queue above capacity.
 *
 * Not being actually a linear queue, this class does not implement the {@code Collection} or {@code Queue} interfaces.
 * The traditional queue interface is split in the traits: {@code Offerable} and {@code Pollable}.
 */
class LinkedBlockingMultiQueue[A, E >: Null](val defaultCapacity: Int = Int.MaxValue) extends Pollable[E] {

  import LinkedBlockingMultiQueue.Node

  private val takeLock = new ReentrantLock
  private val notEmpty = takeLock.newCondition

  private val childrenMap = new ConcurrentHashMap[A, SubQueue]()

  private var priorityGroups = IndexedSeq[PriorityGroup]()

  def subQueues() = childrenMap.toMap

  def totalSize() = childrenMap.values.foldLeft(0)(_ + _.size)

  private def getPriorityGroups() = {
    val groups = for ((priority, group) <- childrenMap.values.groupBy(_.priority).toSeq.sortBy(_._1)) yield {
      new PriorityGroup(group.toIndexedSeq)
    }
    groups.toIndexedSeq
  }

  private class PriorityGroup(val queues: IndexedSeq[SubQueue]) {

    private var nextIdx = 0

    /*
     * Called inside takeLock
     */
    def dequeue(): (SubQueue, E) = {
      // Reset in case of child removal
      if (nextIdx == queues.size)
        nextIdx = 0
      val startIdx = nextIdx
      do {
        val child = queues(nextIdx)
        nextIdx += 1
        if (nextIdx == queues.size)
          nextIdx = 0
        if (child.enabled && child.size > 0)
          return (child, child.dequeue())
      } while (nextIdx != startIdx)
      null
    }

    def isEmpty(): Boolean = {
      // Use old-school while loops to avoid returning inside closures, which uses exceptions and can be slow
      var i = 0
      while (i < queues.size) {
        if (!queues(i).isEmpty)
          return false
        i += 1
      }
      true
    }

    def totalSize(): Int = queues.map(_.size).sum

  }

  /**
   * Add a sub queue.
   *
   * @param priority the queue priority, a lower number means higher priority
   */
  def addSubQueue(key: A, capacity: Int = defaultCapacity, priority: Int = 100) = {
    val part = new SubQueue(capacity, priority)
    takeLock.lock()
    try {
      if (childrenMap.contains(key))
        throw new IllegalArgumentException("Key already present: " + key)
      childrenMap.put(key, part)
      priorityGroups = getPriorityGroups()
    } finally {
      takeLock.unlock()
    }
    part
  }

  def removeSubQueue(key: A): Option[SubQueue] = {
    takeLock.lock()
    try {
      val removed = childrenMap.remove(key)
      if (removed != null)
        priorityGroups = getPriorityGroups()
      Option(removed)
    } finally {
      takeLock.unlock()
    }
  }

  def isEmpty(): Boolean = {
    takeLock.lock()
    try {
      // Use an old-school while loop to avoid returning inside closures, which uses exceptions and can be slow
      var i = 0
      while (i < priorityGroups.size) {
        if (!priorityGroups(i).isEmpty)
          return false
        i += 1
      }
      true
    } finally {
      takeLock.unlock()
    }
  }

  def getSubQueue(key: A) = Option(childrenMap.get(key))

  def getOrCreateSubQueue(key: A): (SubQueue, Boolean) = {
    val existent = childrenMap.get(key)
    if (existent != null) {
      (existent, false)
    } else {
      takeLock.lock()
      try {
        val existent = childrenMap.get(key)
        if (existent == null)
          (addSubQueue(key), true)
        else
          (existent, false)
      } finally {
        takeLock.unlock()
      }
    }
  }

  /**
   * Signals a waiting take. Called only from put/offer (which do not otherwise ordinarily lock takeLock.)
   */
  private def signalNotEmpty(): Unit = {
    takeLock.lock()
    try {
      notEmpty.signal()
    } finally {
      takeLock.unlock()
    }
  }

  def poll(timeout: Long, unit: TimeUnit): E = {
    var remaining = unit.toNanos(timeout)
    takeLock.lockInterruptibly()
    val (subQueue, oldSize, elem) = try {
      while (!anyQueueHasAnything()) {
        if (remaining <= 0)
          return null
        remaining = notEmpty.awaitNanos(remaining)
      }
      // at this point we know there is an element
      val (subQueue, elem) = deque()
      val oldSize = subQueue.count.getAndDecrement()
      if (oldSize > 1) {
        // subqueue has already elements, notify next poller
        notEmpty.signal()
      }
      (subQueue, oldSize, elem)
    } finally {
      takeLock.unlock()
    }
    if (oldSize == subQueue.capacity) {
      // we just took an element from a full queue, notify any blocked offers
      subQueue.signalNotFull()
    }
    elem
  }

  /*
   * (Must be called inside takeLock)
   */
  private def deque() = {
    var dequed: (SubQueue, E) = null
    // ordered iteration, begin with lower index (highest priority)
    var i = 0
    while (i < priorityGroups.size && dequed == null) {
      dequed = priorityGroups(i).dequeue()
      i += 1
    }
    dequed
  }

  /*
   * (Must be called inside takeLock)
   */
  private def anyQueueHasAnything(): Boolean = {
    // Use old-school while loops to avoid returning inside closures, which uses exceptions and is slow
    var i = 0
    while (i < priorityGroups.size) {
      val pg = priorityGroups(i)
      var j = 0
      while (j < pg.queues.size) {
        val child = pg.queues(j)
        if (child.enabled && child.size > 0)
          return true
        j += 1
      }
      i += 1
    }
    false
  }

  class SubQueue(val capacity: Int, val priority: Int) extends Offerable[E] {

    if (capacity <= 0)
      throw new IllegalArgumentException

    private val putLock = new ReentrantLock
    private val notFull = putLock.newCondition

    private[LinkedBlockingMultiQueue] val count = new AtomicInteger

    private[LinkedBlockingMultiQueue] var enabled = true

    /**
     * Head of linked list. Invariant: head.item == null
     */
    private var head = new Node[E](null)

    /**
     * Tail of linked list. Invariant: last.next == null
     */
    private var last = head

    def clear(): Unit = {
      putLock.lock()
      takeLock.lock()
      try {
        var h = head
        var p = h.next
        while (p != null) {
          // help GC
          h.next = h
          p.item = null
          h = p
          p = h.next
        }
        head = last;
        if (count.getAndSet(0) == capacity)
          notFull.signal()
      } finally {
        putLock.unlock()
        takeLock.unlock()
      }
    }

    def enable(status: Boolean): Unit = {
      takeLock.lock()
      try {
        enabled = status
        if (status) {
          // potentially unblock waiting polls
          notEmpty.signal()
        }
      } finally {
        takeLock.unlock()
      }
    }

    def isEnabled(): Boolean = {
      takeLock.lock()
      try {
        enabled
      } finally {
        takeLock.unlock()
      }
    }

    private[LinkedBlockingMultiQueue] def signalNotFull(): Unit = {
      putLock.lock()
      try {
        notFull.signal()
      } finally {
        putLock.unlock()
      }
    }

    private def enqueue(node: Node[E]): Unit = {
      last.next = node
      last = node
    }

    /**
     * Return the number of elements in this queue.
     */
    def size() = count.get()

    def isEmpty() = size == 0

    def offer(e: E, timeout: Long, unit: TimeUnit): Boolean = {
      if (e == null)
        throw new NullPointerException
      var nanos = unit.toNanos(timeout)
      var oldSize = -1
      putLock.lockInterruptibly()
      try {
        while (count.get() == capacity) {
          if (nanos <= 0)
            return false
          nanos = notFull.awaitNanos(nanos)
        }
        enqueue(new Node[E](e))
        oldSize = count.getAndIncrement()
        if (oldSize + 1 < capacity) {
          // queue not full after adding, notify next offerer
          notFull.signal()
        }
      } finally {
        putLock.unlock()
      }
      if (oldSize == 0) {
        // just added an element to an empty queue, notify pollers
        signalNotEmpty()
      }
      true
    }

    def offer(e: E): Boolean = offer(e, 0, TimeUnit.NANOSECONDS)

    /**
     * Removes a node from head of queue.
     * (Must be called inside takeLock)
     * @return the node
     */
    private[LinkedBlockingMultiQueue] def dequeue(): E = {
      assert(size > 0)
      val h = head
      val first = h.next
      h.next = h // help GC
      head = first
      val x = first.item
      first.item = null
      x
    }

  }

}

object LinkedBlockingMultiQueue {

  class Node[E >: Null](var item: E) {
    /**
     * One of:
     * - the real successor Node
     * - this Node, meaning the successor is head.next
     * - null, meaning there is no successor (this is the last node)
     */
    var next: Node[E] = null
  }

}
