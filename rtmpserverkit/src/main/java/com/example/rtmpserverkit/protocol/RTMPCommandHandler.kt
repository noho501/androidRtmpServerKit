package com.example.rtmpserverkit.protocol

import java.io.OutputStream

/**
 * Handles RTMP command messages (connect, createStream, publish, releaseStream, FCPublish).
 * Sends appropriate RTMP responses over the output stream.
 */
internal class RTMPCommandHandler(
    private val output: OutputStream,
    private val onPublish: ((String) -> Unit)?
) {

    private var nextStreamId = 1

    /**
     * Process a decoded RTMP command message.
     */
    fun handleCommand(commandName: String, transactionId: Double, args: List<Any?>) {
        when (commandName) {
            "connect" -> handleConnect(transactionId)
            "releaseStream" -> handleReleaseStream(transactionId)
            "FCPublish" -> handleFCPublish(transactionId, args)
            "createStream" -> handleCreateStream(transactionId)
            "publish" -> handlePublish(args)
            "deleteStream" -> { /* ignore */
            }

            "FCUnpublish" -> { /* ignore */
            }

            "@setDataFrame" -> { /* ignore metadata */
            }

            else -> { /* ignore unknown */
            }
        }
    }

    private fun handleConnect(transactionId: Double) {
        // Send WindowAcknowledgementSize
        sendWindowAckSize(2500000)
        // Send SetPeerBandwidth
        sendSetPeerBandwidth(2500000, 2)
        // Send SetChunkSize
        sendSetChunkSize(4096)
        // Send _result
        sendConnectResult(transactionId)
    }

    private fun handleReleaseStream(transactionId: Double) {
        sendSimpleResult(transactionId)
    }

    private fun handleFCPublish(transactionId: Double, args: List<Any?>) {
        sendSimpleResult(transactionId)
        val streamKey = args.getOrNull(1) as? String ?: ""
        sendOnFCPublish(streamKey)
    }

    private fun handleCreateStream(transactionId: Double) {
        val streamId = nextStreamId++
        sendCreateStreamResult(transactionId, streamId.toDouble())
    }

    private fun handlePublish(args: List<Any?>) {
        val streamKey = (args.getOrNull(3) as? String) ?: (args.getOrNull(1) as? String) ?: ""
        sendOnStatus("NetStream.Publish.Start", "Start publishing")
        onPublish?.invoke(streamKey)
    }

    // --- AMF0 Response Builders ---

    private fun sendWindowAckSize(size: Int) {
        val payload = ByteArray(4)
        payload[0] = (size shr 24).toByte()
        payload[1] = (size shr 16).toByte()
        payload[2] = (size shr 8).toByte()
        payload[3] = size.toByte()
        writeChunk(RTMPMessage.TYPE_WINDOW_ACK_SIZE, 0, payload, csid = 2)
    }

    private fun sendSetPeerBandwidth(size: Int, limitType: Int) {
        val payload = ByteArray(5)
        payload[0] = (size shr 24).toByte()
        payload[1] = (size shr 16).toByte()
        payload[2] = (size shr 8).toByte()
        payload[3] = size.toByte()
        payload[4] = limitType.toByte()
        writeChunk(RTMPMessage.TYPE_SET_PEER_BANDWIDTH, 0, payload, csid = 2)
    }

    private fun sendSetChunkSize(size: Int) {
        val payload = ByteArray(4)
        payload[0] = (size shr 24).toByte()
        payload[1] = (size shr 16).toByte()
        payload[2] = (size shr 8).toByte()
        payload[3] = size.toByte()
        writeChunk(RTMPMessage.TYPE_SET_CHUNK_SIZE, 0, payload, csid = 2)
    }

    private fun sendConnectResult(transactionId: Double) {
        val amf = buildAmf0 {
            writeString("_result")
            writeNumber(transactionId)
            writeObjectStart()
            writeProperty("fmsVer", "FMS/3,5,3,888")
            writeProperty("capabilities", 127.0)
            writeObjectEnd()
            writeObjectStart()
            writeProperty("level", "status")
            writeProperty("code", "NetConnection.Connect.Success")
            writeProperty("description", "Connection succeeded.")
            writeProperty("objectEncoding", 0.0)
            writeObjectEnd()
        }
        writeChunk(RTMPMessage.TYPE_COMMAND_AMF0, 0, amf, csid = 3)
    }

    private fun sendSimpleResult(transactionId: Double) {
        val amf = buildAmf0 {
            writeString("_result")
            writeNumber(transactionId)
            writeNull()
            writeNull()
        }
        writeChunk(RTMPMessage.TYPE_COMMAND_AMF0, 0, amf, csid = 3)
    }

    private fun sendCreateStreamResult(transactionId: Double, streamId: Double) {
        val amf = buildAmf0 {
            writeString("_result")
            writeNumber(transactionId)
            writeNull()
            writeNumber(streamId)
        }
        writeChunk(RTMPMessage.TYPE_COMMAND_AMF0, 0, amf, csid = 3)
    }

    private fun sendOnFCPublish(streamKey: String) {
        val amf = buildAmf0 {
            writeString("onFCPublish")
            writeNumber(0.0)
            writeNull()
            writeObjectStart()
            writeProperty("code", "NetStream.Publish.Start")
            writeProperty("description", streamKey)
            writeObjectEnd()
        }
        writeChunk(RTMPMessage.TYPE_COMMAND_AMF0, 0, amf, csid = 3)
    }

    private fun sendOnStatus(code: String, description: String) {
        val amf = buildAmf0 {
            writeString("onStatus")
            writeNumber(0.0)
            writeNull()
            writeObjectStart()
            writeProperty("level", "status")
            writeProperty("code", code)
            writeProperty("description", description)
            writeObjectEnd()
        }
        writeChunk(RTMPMessage.TYPE_COMMAND_AMF0, 1, amf, csid = 5)
    }

    // --- Chunk writing ---

    private fun writeChunk(messageType: Int, streamId: Long, payload: ByteArray, csid: Int = 3) {
        synchronized(output) {
            try {
                val chunkSize = 4096
                val header = buildChunkHeader(csid, messageType, streamId, payload.size)
                output.write(header)

                var offset = 0
                while (offset < payload.size) {
                    val toWrite = minOf(chunkSize, payload.size - offset)
                    if (offset > 0) {
                        // Type 3 continuation header
                        output.write(0xC0 or (csid and 0x3F))
                    }
                    output.write(payload, offset, toWrite)
                    offset += toWrite
                }
                output.flush()
            } catch (e: Exception) {
                // Ignore write errors (client may have disconnected)
            }
        }
    }

    private fun buildChunkHeader(
        csid: Int,
        messageType: Int,
        streamId: Long,
        payloadSize: Int
    ): ByteArray {
        val buf = mutableListOf<Byte>()
        // Basic header: fmt=0, csid
        buf.add((csid and 0x3F).toByte())
        // Timestamp: 0
        buf.add(0); buf.add(0); buf.add(0)
        // Message length
        buf.add((payloadSize shr 16).toByte())
        buf.add((payloadSize shr 8).toByte())
        buf.add(payloadSize.toByte())
        // Message type
        buf.add(messageType.toByte())
        // Stream ID (little endian)
        buf.add((streamId and 0xFF).toByte())
        buf.add(((streamId shr 8) and 0xFF).toByte())
        buf.add(((streamId shr 16) and 0xFF).toByte())
        buf.add(((streamId shr 24) and 0xFF).toByte())
        return buf.toByteArray()
    }

    // --- AMF0 builder ---

    private fun buildAmf0(block: Amf0Builder.() -> Unit): ByteArray {
        val builder = Amf0Builder()
        builder.block()
        return builder.toByteArray()
    }

    private class Amf0Builder {
        private val buf = java.io.ByteArrayOutputStream()

        fun writeNumber(value: Double) {
            buf.write(0x00) // AMF0 number type
            val bits = java.lang.Double.doubleToRawLongBits(value)
            for (i in 7 downTo 0) buf.write(((bits shr (i * 8)) and 0xFF).toInt())
        }

        fun writeString(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            buf.write(0x02) // AMF0 string type
            buf.write((bytes.size shr 8) and 0xFF)
            buf.write(bytes.size and 0xFF)
            buf.write(bytes)
        }

        fun writeNull() {
            buf.write(0x05)
        }

        fun writeObjectStart() {
            buf.write(0x03)
        }

        fun writeObjectEnd() {
            buf.write(0x00)
            buf.write(0x00)
            buf.write(0x09)
        }

        fun writeProperty(key: String, value: String) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            buf.write((keyBytes.size shr 8) and 0xFF)
            buf.write(keyBytes.size and 0xFF)
            buf.write(keyBytes)
            writeString(value)
        }

        fun writeProperty(key: String, value: Double) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            buf.write((keyBytes.size shr 8) and 0xFF)
            buf.write(keyBytes.size and 0xFF)
            buf.write(keyBytes)
            writeNumber(value)
        }

        fun toByteArray(): ByteArray = buf.toByteArray()
    }
}
