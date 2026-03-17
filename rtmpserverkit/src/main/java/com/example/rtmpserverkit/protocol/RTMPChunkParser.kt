package com.example.rtmpserverkit.protocol

import com.example.rtmpserverkit.utils.ByteReader
import java.io.InputStream

/**
 * Incremental RTMP chunk parser.
 *
 * Reads chunks from the input stream incrementally and reassembles them
 * into complete RTMP messages. Handles:
 *  - All 4 chunk header formats (type 0–3)
 *  - Extended timestamps
 *  - Multiple chunk streams
 *  - Fragmented chunks
 */
internal class RTMPChunkParser {

    private var chunkSize = 128
    private val chunkStreams = HashMap<Int, RTMPChunkStream>()

    fun setChunkSize(size: Int) {
        chunkSize = size.coerceAtLeast(1)
    }

    /**
     * Reads the next complete RTMP message from the stream.
     * Blocks until a full message is assembled.
     * Returns null on stream end or error.
     */
    fun readMessage(input: InputStream): RTMPMessage? {
        return try {
            readChunk(input)
        } catch (e: Exception) {
            null
        }
    }

    private fun readChunk(input: InputStream): RTMPMessage? {
        // Read basic header (first byte)
        val firstByte = input.read()
        if (firstByte < 0) return null

        val fmt = (firstByte shr 6) and 0x03
        var csid = firstByte and 0x3F

        // Handle extended CSID
        csid = when (csid) {
            0 -> ByteReader.readUInt8(input) + 64
            1 -> {
                val b0 = ByteReader.readUInt8(input)
                val b1 = ByteReader.readUInt8(input)
                (b1 shl 8) + b0 + 64
            }
            else -> csid
        }

        val cs = chunkStreams.getOrPut(csid) { RTMPChunkStream(csid) }

        // Read message header based on fmt
        when (fmt) {
            0 -> {
                // Type 0: full header
                var ts = ByteReader.readUInt24(input).toLong()
                cs.messageLength = ByteReader.readUInt24(input)
                cs.messageType = ByteReader.readUInt8(input)
                cs.messageStreamId = ByteReader.readUInt32LE(input)
                if (ts == 0xFFFFFF.toLong()) {
                    ts = ByteReader.readUInt32(input)
                }
                cs.timestamp = ts
                cs.timestampDelta = 0
                cs.payload = ByteArray(cs.messageLength)
                cs.bytesRead = 0
            }
            1 -> {
                // Type 1: no stream ID
                var delta = ByteReader.readUInt24(input).toLong()
                cs.messageLength = ByteReader.readUInt24(input)
                cs.messageType = ByteReader.readUInt8(input)
                if (delta == 0xFFFFFF.toLong()) {
                    delta = ByteReader.readUInt32(input)
                }
                cs.timestampDelta = delta
                cs.timestamp += delta
                cs.payload = ByteArray(cs.messageLength)
                cs.bytesRead = 0
            }
            2 -> {
                // Type 2: timestamp delta only
                var delta = ByteReader.readUInt24(input).toLong()
                if (delta == 0xFFFFFF.toLong()) {
                    delta = ByteReader.readUInt32(input)
                }
                cs.timestampDelta = delta
                cs.timestamp += delta
                cs.payload = ByteArray(cs.messageLength)
                cs.bytesRead = 0
            }
            3 -> {
                // Type 3: continuation (no header)
                // Check if we need a new payload buffer (start of message)
                if (cs.bytesRead == 0 && cs.messageLength > 0) {
                    cs.payload = ByteArray(cs.messageLength)
                }
                if (cs.bytesRead == cs.messageLength) {
                    // New message using previous header
                    cs.timestamp += cs.timestampDelta
                    cs.payload = ByteArray(cs.messageLength)
                    cs.bytesRead = 0
                }
            }
        }

        // Read chunk data
        val remaining = cs.messageLength - cs.bytesRead
        val toRead = minOf(remaining, chunkSize)
        val read = readFully(input, cs.payload, cs.bytesRead, toRead)
        if (read < 0) return null
        cs.bytesRead += read

        // Check if message is complete
        return if (cs.bytesRead >= cs.messageLength) {
            cs.bytesRead = 0
            RTMPMessage(
                type = cs.messageType,
                streamId = cs.messageStreamId,
                timestamp = cs.timestamp,
                payload = cs.payload.copyOf()
            )
        } else {
            // Need more chunks; return special sentinel by recursing
            readChunk(input)
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray, offset: Int, length: Int): Int {
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buf, offset + totalRead, length - totalRead)
            if (read < 0) return -1
            totalRead += read
        }
        return totalRead
    }
}
