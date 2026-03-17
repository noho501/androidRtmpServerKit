package com.example.rtmpserverkit.utils

/**
 * A simple circular buffer (ring buffer) for byte data.
 * Not thread-safe; external synchronization required.
 */
internal class RingBuffer(private val capacity: Int) {

    private val buffer = ByteArray(capacity)
    private var readPos = 0
    private var writePos = 0
    private var size = 0

    val available: Int get() = size
    val remaining: Int get() = capacity - size

    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Boolean {
        if (length > remaining) return false
        var written = 0
        while (written < length) {
            buffer[writePos] = data[offset + written]
            writePos = (writePos + 1) % capacity
            written++
        }
        size += length
        return true
    }

    fun read(dest: ByteArray, offset: Int = 0, length: Int = dest.size - offset): Int {
        val toRead = minOf(length, size)
        for (i in 0 until toRead) {
            dest[offset + i] = buffer[readPos]
            readPos = (readPos + 1) % capacity
        }
        size -= toRead
        return toRead
    }

    fun peek(dest: ByteArray, offset: Int = 0, length: Int = dest.size - offset): Int {
        val toRead = minOf(length, size)
        var pos = readPos
        for (i in 0 until toRead) {
            dest[offset + i] = buffer[pos]
            pos = (pos + 1) % capacity
        }
        return toRead
    }

    fun skip(count: Int): Int {
        val toSkip = minOf(count, size)
        readPos = (readPos + toSkip) % capacity
        size -= toSkip
        return toSkip
    }

    fun clear() {
        readPos = 0
        writePos = 0
        size = 0
    }
}
