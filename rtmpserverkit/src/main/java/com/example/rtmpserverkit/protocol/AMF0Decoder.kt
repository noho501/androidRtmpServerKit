package com.example.rtmpserverkit.protocol

import com.example.rtmpserverkit.utils.ByteReader

/**
 * Decodes AMF0 encoded values.
 * Supports: Number, Boolean, String, Object, Null, Undefined, EcmaArray.
 */
internal object AMF0Decoder {

    // AMF0 type markers
    private const val TYPE_NUMBER = 0x00
    private const val TYPE_BOOLEAN = 0x01
    private const val TYPE_STRING = 0x02
    private const val TYPE_OBJECT = 0x03
    private const val TYPE_NULL = 0x05
    private const val TYPE_UNDEFINED = 0x06
    private const val TYPE_ECMA_ARRAY = 0x08
    private const val TYPE_OBJECT_END = 0x09
    private const val TYPE_LONG_STRING = 0x0C
    private const val TYPE_STRICT_ARRAY = 0x0A

    /**
     * Decodes all AMF0 values from the given byte array.
     */
    fun decodeAll(data: ByteArray): List<Any?> {
        val result = mutableListOf<Any?>()
        var offset = 0
        while (offset < data.size) {
            val (value, bytesConsumed) = decode(data, offset)
            result.add(value)
            offset += bytesConsumed
            if (bytesConsumed == 0) break
        }
        return result
    }

    /**
     * Decodes a single AMF0 value at the given offset.
     * Returns Pair(value, bytesConsumed).
     */
    fun decode(data: ByteArray, offset: Int): Pair<Any?, Int> {
        if (offset >= data.size) return Pair(null, 0)

        return when (val type = ByteReader.getUInt8(data, offset)) {
            TYPE_NUMBER -> {
                if (offset + 9 > data.size) Pair(0.0, 1)
                else {
                    val bits = readDoubleBits(data, offset + 1)
                    Pair(java.lang.Double.longBitsToDouble(bits), 9)
                }
            }
            TYPE_BOOLEAN -> {
                if (offset + 2 > data.size) Pair(false, 1)
                else Pair(data[offset + 1] != 0.toByte(), 2)
            }
            TYPE_STRING -> {
                if (offset + 3 > data.size) Pair("", 1)
                else {
                    val len = ByteReader.getUInt16(data, offset + 1)
                    if (offset + 3 + len > data.size) Pair("", 3)
                    else {
                        val str = String(data, offset + 3, len, Charsets.UTF_8)
                        Pair(str, 3 + len)
                    }
                }
            }
            TYPE_OBJECT -> {
                val (obj, consumed) = decodeObject(data, offset + 1)
                Pair(obj, 1 + consumed)
            }
            TYPE_NULL, TYPE_UNDEFINED -> Pair(null, 1)
            TYPE_ECMA_ARRAY -> {
                // Skip the 4-byte associative count
                val (obj, consumed) = decodeObject(data, offset + 5)
                Pair(obj, 5 + consumed)
            }
            TYPE_LONG_STRING -> {
                if (offset + 5 > data.size) Pair("", 1)
                else {
                    val len = ByteReader.getUInt32(data, offset + 1).toInt()
                    if (offset + 5 + len > data.size) Pair("", 5)
                    else {
                        val str = String(data, offset + 5, len, Charsets.UTF_8)
                        Pair(str, 5 + len)
                    }
                }
            }
            TYPE_STRICT_ARRAY -> {
                if (offset + 5 > data.size) return Pair(emptyList<Any?>(), 1)
                val count = ByteReader.getUInt32(data, offset + 1).toInt()
                val list = mutableListOf<Any?>()
                var pos = offset + 5
                repeat(count) {
                    val (v, c) = decode(data, pos)
                    list.add(v)
                    pos += c
                }
                Pair(list, pos - offset)
            }
            else -> Pair(null, 1)
        }
    }

    private fun decodeObject(data: ByteArray, startOffset: Int): Pair<Map<String, Any?>, Int> {
        val obj = mutableMapOf<String, Any?>()
        var offset = startOffset
        while (offset + 2 < data.size) {
            val keyLen = ByteReader.getUInt16(data, offset)
            offset += 2
            if (keyLen == 0 && offset < data.size && ByteReader.getUInt8(data, offset) == TYPE_OBJECT_END) {
                offset += 1
                break
            }
            if (offset + keyLen > data.size) break
            val key = String(data, offset, keyLen, Charsets.UTF_8)
            offset += keyLen
            val (value, consumed) = decode(data, offset)
            obj[key] = value
            offset += consumed
        }
        return Pair(obj, offset - startOffset)
    }

    private fun readDoubleBits(data: ByteArray, offset: Int): Long {
        var bits = 0L
        for (i in 0..7) {
            bits = (bits shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return bits
    }
}
