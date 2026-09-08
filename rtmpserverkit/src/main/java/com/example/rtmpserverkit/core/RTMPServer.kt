package com.example.rtmpserverkit.core

import android.util.Log
import com.example.rtmpserverkit.media.MediaCodecDecoder
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
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

    // Map manage connection by streamKey
    private val connectionsMap = ConcurrentHashMap<String, RTMPConnection>()

    var onFrame: ((String, ByteArray) -> Unit)? = null
    var onPublish: ((String) -> Unit)? = null
    var onDisconnect: ((String) -> Unit)? = null

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
                            onPublish = { key, connection ->
                                connectionsMap[key] = connection
                                onPublish?.invoke(key)
                            },
                            onFrame = { key, frame ->
                                onFrame?.invoke(key, frame)
                            },
                            onDisconnect = { key ->
                                if (key.isNotEmpty()) {
                                    connectionsMap.remove(key)
                                }
                                onDisconnect?.invoke(key)
                                Log.d(TAG, "Client disconnected: $key")
                            }
                        )
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
        connectionsMap.values.forEach { it.close() }
        connectionsMap.clear()
        executor.shutdownNow()
    }

    fun getDecoder(streamKey: String): MediaCodecDecoder? {
        return connectionsMap[streamKey]?.decoder
    }
}
