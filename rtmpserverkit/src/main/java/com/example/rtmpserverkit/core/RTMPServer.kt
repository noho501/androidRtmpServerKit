package com.example.rtmpserverkit.core

import android.util.Log
import com.example.rtmpserverkit.media.MediaCodecDecoder
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The main RTMP server. Listens for incoming connections and spawns
 * an RTMPConnection handler for each client on a thread pool.
 */
internal class RTMPServer {

    companion object {
        private const val TAG = "RTMPServer"
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val connections = mutableListOf<RTMPConnection>()

    var decoder: MediaCodecDecoder? = null
    var onFrame: ((ByteArray) -> Unit)? = null
    var onPublish: ((String) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null

    fun start(port: Int = 1935) {
        if (running.getAndSet(true)) return

        Thread({
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                Log.i(TAG, "RTMP server listening on port $port")

                while (running.get()) {
                    try {
                        val socket = ss.accept()
                        val conn = RTMPConnection(
                            socket = socket,
                            decoder = decoder,
                            onPublish = onPublish,
                            onFrame = onFrame,
                            onDisconnect = {
                                onDisconnect?.invoke()
                                Log.d(TAG, "Client disconnected")
                            }
                        )
                        synchronized(connections) { connections.add(conn) }
                        executor.submit(conn)
                    } catch (e: Exception) {
                        if (running.get()) {
                            Log.w(TAG, "Accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }, "RTMP-Accept").start()
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        synchronized(connections) {
            connections.forEach { it.close() }
            connections.clear()
        }
        executor.shutdownNow()
    }
}
