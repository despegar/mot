package mot.queue

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import scala.collection.JavaConversions._
import java.util.concurrent.atomic.AtomicInteger
import scala.IndexedSeq
import mot.util.Pollable
import java.util.concurrent.ConcurrentHashMap
import mot.util.Offerable

class LinkedBlockingMultiQueue[A, E >: Null <: AnyRef](val defaultCapacity: Int) extends Pollable[E] {

  import LinkedBlockingMultiQueue.Node

  private val takeLock = new ReentrantLock
  private val notEmpty = takeLock.newCondition

  val childrenMap = new ConcurrentHashMap[A, QueuePart[E]]()

  var priorityGroups = IndexedSeq[PriorityGroup]()

  def children() = childrenMap.toMap

  def totalSize() = childrenMap.values.foldLeft(0)(_ + _.size)

  private def getPriorityGroups() = {
    val groups = for ((priority, group) <- childrenMap.values.groupBy(_.priority).toSeq.sortBy(_._1)) yield {
      new PriorityGroup(group.toIndexedSeq)
    }
    groups.toIndexedSeq
  }

  class PriorityGroup(val children: IndexedSeq[QueuePart[E]]) {

    var nextIdx = 0

    def dequeue(): (QueuePart[E], E) = {
      // Reset in case of child removal
      if (nextIdx == children.size)
        nextIdx = 0
      val startIdx = nextIdx
      do {
        val child = children(nextIdx)
        nextIdx += 1
        if (nextIdx == children.size)
          nextIdx = 0
        if (child.active && child.size > 0)
          return (child, child.dequeue())
      } while (nextIdx != startIdx)
      null
    }

    def isEmpty(): Boolean = {
      // Use old-school while loops to avoid returning inside closures, which uses exceptions and is slow
      var i = 0
      while (i < children.size) {
        if (!children(i).isEmpty)
          return false
        i += 1
      }
      true
    }

    def totalSize() = children.map(_.size).sum

  }

  // Lower number means higher priority
  def addChild(key: A, capacity: Int = defaultCapacity, priority: Int = 100) = {
    val part = new QueuePart[E](capacity, priority)
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

  def removeChild(key: A) {
    takeLock.lock()
    try {
      childrenMap.remove(key)
      priorityGroups = getPriorityGroups()
    } finally {
      takeLock.unlock()
    }
  }

  def isEmpty(): Boolean = {
    takeLock.lock()
    try {
      // Use old-school while loops to avoid returning inside closures, which uses exceptions and is slow
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

  def getChild(key: A) = Option(childrenMap.get(key))
  
  def getOrCreateChild(key: A): (QueuePart[E], Boolean) = {
    val existent = childrenMap.get(key)
    if (existent != null) {
      (existent, false)
    } else {
      takeLock.lock()
      try {
        val existent = childrenMap.get(key)
        if (existent == null)
          (addChild(key), true)
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
  def signalNotEmpty() {
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
    val (q, c, x) = try {
      while (!anyQueueHasAnything()) {
        if (remaining <= 0)
          return null
        remaining = notEmpty.awaitNanos(remaining)
      }
      var dequed: (QueuePart[E], E) = null
      var i = 0
      // ordered iteration, begin with lower index (highest priority)
      while (i < priorityGroups.size && dequed == null) {
        dequed = priorityGroups(i).dequeue()
        i += 1
      }
      assert(dequed != null)
      val (q, x) = dequed
      val c = q.count.getAndDecrement()
      if (c > 1)
        notEmpty.signal()
      (q, c, x)
    } finally {
      takeLock.unlock()
    }
    if (c == q.capacity)
      q.signalNotFull()
    x
  }

  private def anyQueueHasAnything(): Boolean = {
    // Use old-school while loops to avoid returning inside closures, which uses exceptions and is slow
    var i = 0
    while (i < priorityGroups.size) {
      val pg = priorityGroups(i)
      var j = 0
      while (j < pg.children.size) {
        val child = pg.children(j)
        if (child.active && child.size > 0)
          return true
        j += 1
      }
      i += 1
    }
    false
  }

  class QueuePart[E >: Null <: AnyRef](val capacity: Int, val priority: Int) extends Offerable[E] {

    if (capacity <= 0)
      throw new IllegalArgumentException

    private val putLock = new ReentrantLock
    private val notFull = putLock.newCondition

    private[LinkedBlockingMultiQueue] val count = new AtomicInteger

    private[LinkedBlockingMultiQueue] var active = true

    /**
     * Head of linked list. Invariant: head.item == null
     */
    private[LinkedBlockingMultiQueue] var head = new Node[E](null)

    /**
     * Tail of linked list. Invariant: last.next == null
     */
    private var last = head

    def enable(status: Boolean) {
      takeLock.lock()
      try {
        active = status
        if (status)
          notEmpty.signal()
      } finally {
        takeLock.unlock()
      }
    }

    def isEnabled() = {
      takeLock.lock()
      try {
        active
      } finally {
        takeLock.unlock()
      }
    }

    private[LinkedBlockingMultiQueue] def signalNotFull() {
      putLock.lock()
      try {
        notFull.signal()
      } finally {
        putLock.unlock()
      }
    }

    /**
     * Links node at end of queue.
     * @param node the node
     */
    private def enqueue(node: Node[E]) {
      last.next = node
      last = node
    }

    /**
     * Returns the number of elements in this queue.
     * @return the number of elements in this queue
     */
    def size() = count.get()

    def isEmpty() = size == 0

    /**
     * Inserts the specified element at the tail of this queue, waiting if
     * necessary up to the specified wait time for space to become available.
     *
     * @return {@code true} if successful, or {@code false} if the specified waiting time elapses before space is
     *   available.
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    def offer(e: E, timeout: Long, unit: TimeUnit): Boolean = {
      if (e == null)
        throw new NullPointerException()
      var nanos = unit.toNanos(timeout)
      var c = -1
      putLock.lockInterruptibly()
      try {
        while (count.get() == capacity) {
          if (nanos <= 0)
            return false
          nanos = notFull.awaitNanos(nanos)
        }
        enqueue(new Node[E](e))
        c = count.getAndIncrement()
        if (c + 1 < capacity)
          notFull.signal()
      } finally {
        putLock.unlock()
      }
      if (c == 0)
        signalNotEmpty()
      true
    }

    def offer(e: E): Boolean = offer(e, 0, TimeUnit.NANOSECONDS)
    
    /**
     * Removes a node from head of queue.
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

  class Node[E >: Null <: AnyRef](var item: E) {
    /**
     * One of:
     * - the real successor Node
     * - this Node, meaning the successor is head.next
     * - null, meaning there is no successor (this is the last node)
     */
    var next: Node[E] = null
  }

}
