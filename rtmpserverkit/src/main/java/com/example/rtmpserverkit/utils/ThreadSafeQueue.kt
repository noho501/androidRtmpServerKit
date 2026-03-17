package com.example.rtmpserverkit.utils

import java.util.LinkedList
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock

/**
 * A thread-safe queue backed by a LinkedList.
 */
internal class ThreadSafeQueue<T>(private val maxSize: Int = Int.MAX_VALUE) {

    private val queue = LinkedList<T>()
    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()

    fun enqueue(item: T): Boolean {
        lock.withLock {
            if (queue.size >= maxSize) return false
            queue.addLast(item)
            notEmpty.signalAll()
            return true
        }
    }

    fun dequeue(timeoutMs: Long = 0): T? {
        lock.withLock {
            if (queue.isEmpty() && timeoutMs > 0) {
                notEmpty.await(timeoutMs, TimeUnit.MILLISECONDS)
            }
            return if (queue.isEmpty()) null else queue.removeFirst()
        }
    }

    fun size(): Int = lock.withLock { queue.size }

    fun clear() = lock.withLock { queue.clear() }

    fun isEmpty(): Boolean = lock.withLock { queue.isEmpty() }
}
