package com.example.rtmpserverkit.utils

import java.io.InputStream

/**
 * Utility class for reading typed values from a stream or byte array.
 */
internal object ByteReader {

    fun readUInt8(input: InputStream): Int {
        val b = input.read()
        if (b < 0) throw java.io.EOFException("End of stream")
        return b
    }

    fun readUInt16(input: InputStream): Int {
        val b0 = readUInt8(input)
        val b1 = readUInt8(input)
        return (b0 shl 8) or b1
    }

    fun readUInt24(input: InputStream): Int {
        val b0 = readUInt8(input)
        val b1 = readUInt8(input)
        val b2 = readUInt8(input)
        return (b0 shl 16) or (b1 shl 8) or b2
    }

    fun readUInt32(input: InputStream): Long {
        val b0 = readUInt8(input).toLong()
        val b1 = readUInt8(input).toLong()
        val b2 = readUInt8(input).toLong()
        val b3 = readUInt8(input).toLong()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readUInt32LE(input: InputStream): Long {
        val b0 = readUInt8(input).toLong()
        val b1 = readUInt8(input).toLong()
        val b2 = readUInt8(input).toLong()
        val b3 = readUInt8(input).toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun readBytes(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buf, offset, count - offset)
            if (read < 0) throw java.io.EOFException("End of stream while reading $count bytes")
            offset += read
        }
        return buf
    }

    // --- ByteArray helpers ---

    fun getUInt8(data: ByteArray, offset: Int): Int = data[offset].toInt() and 0xFF

    fun getUInt16(data: ByteArray, offset: Int): Int =
        (getUInt8(data, offset) shl 8) or getUInt8(data, offset + 1)

    fun getUInt24(data: ByteArray, offset: Int): Int =
        (getUInt8(data, offset) shl 16) or (getUInt8(data, offset + 1) shl 8) or getUInt8(data, offset + 2)

    fun getUInt32(data: ByteArray, offset: Int): Long {
        val b0 = getUInt8(data, offset).toLong()
        val b1 = getUInt8(data, offset + 1).toLong()
        val b2 = getUInt8(data, offset + 2).toLong()
        val b3 = getUInt8(data, offset + 3).toLong()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun putUInt32BE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }
}
