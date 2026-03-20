package com.example.rtmpserverkit.publicapi

import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.example.rtmpserverkit.core.RTMPServer
import com.example.rtmpserverkit.media.MediaCodecDecoder
import com.example.rtmpserverkit.render.VideoRenderer

/**
 * Public API for the RTMP Server SDK.
 *
 * Usage:
 * ```kotlin
 * val server = RTMPServerPublic()
 * server.onPublish = { streamKey -> Log.d("RTMP", "Publishing: $streamKey") }
 * server.onDisconnect = { Log.d("RTMP", "Client disconnected") }
 * server.attachSurface(surfaceView)
 * server.start()
 * // ...
 * server.stop()
 * ```
 */
class RTMPServerPublic {

    /** Called when a client starts publishing, with the stream key. */
    var onPublish: ((String) -> Unit)? = null

    /** Called when a client disconnects. */
    var onDisconnect: (() -> Unit)? = null

    /**
     * Called for each decoded frame as raw Annex-B NALU bytes.
     * Note: For rendering, use [attachSurface] instead.
     */
    var onFrame: ((ByteArray) -> Unit)? = null

    private val decoder = MediaCodecDecoder()
    private val renderer = VideoRenderer(decoder)
    private val server = RTMPServer().also { s ->
        s.decoder = decoder
        s.onPublish = { key -> onPublish?.invoke(key) }
        s.onDisconnect = { onDisconnect?.invoke() }
        s.onFrame = { frame -> onFrame?.invoke(frame) }
    }

    /**
     * Starts the RTMP server on the specified port.
     * @param port TCP port to listen on (default: 1935)
     */
    fun start(port: Int = 1935) {
        server.start(port)
    }

    /** Stops the RTMP server and releases resources. */
    fun stop() {
        server.stop()
        renderer.release()
        decoder.release()
    }

    /**
     * Attaches a [SurfaceView] for video rendering.
     * Call before or after [start].
     */
    fun attachSurface(surfaceView: SurfaceView) {
        renderer.attachSurfaceView(surfaceView)
    }

    /**
     * Attaches a [TextureView] for video rendering.
     * Call before or after [start].
     */
    fun attachSurface(textureView: TextureView) {
        renderer.attachTextureView(textureView)
    }

    /**
     * Attaches a raw [Surface] for video rendering.
     * Call before or after [start].
     */
    fun attachSurface(surface: Surface) {
        renderer.attachSurface(surface)
    }
}
