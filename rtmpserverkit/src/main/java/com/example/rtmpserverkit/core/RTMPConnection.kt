package com.example.rtmpserverkit.core

import android.util.Log
import com.example.rtmpserverkit.media.H264Parser
import com.example.rtmpserverkit.media.MediaCodecDecoder
import com.example.rtmpserverkit.protocol.AMF0Decoder
import com.example.rtmpserverkit.protocol.RTMPChunkParser
import com.example.rtmpserverkit.protocol.RTMPCommandHandler
import com.example.rtmpserverkit.protocol.RTMPHandshake
import com.example.rtmpserverkit.protocol.RTMPMessage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Socket

/**
 * Handles a single RTMP client connection on its own thread.
 */
internal class RTMPConnection(
    private val socket: Socket,
    private val decoder: MediaCodecDecoder?,
    private val onPublish: ((String) -> Unit)?,
    private val onFrame: ((ByteArray) -> Unit)?,
    private val onDisconnect: (() -> Unit)?
) : Runnable {

    companion object {
        private const val TAG = "RTMPConnection"
    }

    @Volatile
    private var running = true

    private var state = RTMPSessionState.CONNECTING

    override fun run() {
        try {
            socket.tcpNoDelay = true
            val input = BufferedInputStream(socket.getInputStream(), 65536)
            val output = BufferedOutputStream(socket.getOutputStream(), 65536)

            state = RTMPSessionState.HANDSHAKING

            // Perform handshake
            val handshake = RTMPHandshake()
            if (!handshake.performServer(input, output)) {
                Log.d(TAG, "RTMP Handshake FAILED with ${socket.remoteSocketAddress}")
                return
            }

            state = RTMPSessionState.CONNECTED

            val chunkParser = RTMPChunkParser()
            val commandHandler = RTMPCommandHandler(output, onPublish)
            val h264Parser = H264Parser()

            // Message processing loop
            while (running) {
                val msg = chunkParser.readMessage(input) ?: break

                when (msg.type) {
                    RTMPMessage.TYPE_SET_CHUNK_SIZE -> {
                        if (msg.payload.size >= 4) {
                            val newSize = ((msg.payload[0].toInt() and 0x7F) shl 24) or
                                    ((msg.payload[1].toInt() and 0xFF) shl 16) or
                                    ((msg.payload[2].toInt() and 0xFF) shl 8) or
                                    (msg.payload[3].toInt() and 0xFF)
                            chunkParser.setChunkSize(newSize)
                        }
                    }

                    RTMPMessage.TYPE_WINDOW_ACK_SIZE -> { /* acknowledge */
                    }

                    RTMPMessage.TYPE_ACK -> { /* acknowledge bytes */
                    }

                    RTMPMessage.TYPE_COMMAND_AMF0 -> {
                        handleCommand(msg.payload, commandHandler)
                    }

                    RTMPMessage.TYPE_DATA_AMF0 -> {
                        // Metadata - usually @setDataFrame
                    }

                    RTMPMessage.TYPE_VIDEO -> {
                        if (state == RTMPSessionState.PUBLISHING || state == RTMPSessionState.CONNECTED) {
                            state = RTMPSessionState.PUBLISHING
                            handleVideo(msg, h264Parser)
                            msg
                        }
                    }

                    RTMPMessage.TYPE_AUDIO -> {
                        // Audio not decoded in this implementation
                    }

                    else -> { /* ignore */
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Connection error: $e")
        } finally {
            state = RTMPSessionState.CLOSED
            runCatching { socket.close() }
            onDisconnect?.invoke()
        }
    }

    fun close() {
        running = false
        runCatching { socket.close() }
    }

    private fun handleCommand(payload: ByteArray, handler: RTMPCommandHandler) {
        try {
            val values = AMF0Decoder.decodeAll(payload)
            if (values.isEmpty()) return
            val commandName = values[0] as? String ?: return
            val transactionId = (values.getOrNull(1) as? Double) ?: 0.0
            handler.handleCommand(commandName, transactionId, values)
        } catch (e: Exception) {
            Log.w(TAG, "Error handling command: ${e.message}")
        }
    }

    private fun handleVideo(msg: RTMPMessage, h264Parser: H264Parser) {
        try {
            val payload = msg.payload
            if (payload.size < 2) return

            val avcPacketType = payload[1].toInt()

            // 1. Process SPS/PPS to create a decoder.
            if (avcPacketType == 0) {
                h264Parser.parseVideoData(payload)
                val sps = h264Parser.spsStore.getSPS()
                val pps = h264Parser.spsStore.getPPS()
                val width = h264Parser.spsStore.width
                val height = h264Parser.spsStore.height

                if (sps != null && pps != null) {
                    decoder?.initWithSPSPPS(sps = sps, pps = pps, width = width, height = height)
                }
                return // Exit after processing the configuration
            }

            // 2. NALU PROCESSING (VIDEO DATA)
            val nalus = h264Parser.parseVideoData(payload) ?: return
            val timestampUs = System.nanoTime() / 1000

            for (nalu in nalus) {
                onFrame?.invoke(nalu)
                decoder?.feedNalu(nalu, timestampUs)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling video: $e")
        }
    }
}
