package com.example.rtmpserverkit.publicapi

import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.example.rtmpserverkit.core.RTMPServer
import com.example.rtmpserverkit.render.VideoRenderer

/**
 * Public API for the RTMP Server SDK.
 */
class RTMPServerPublic {

    /** Called when a client starts publishing, with the stream key. */
    var onPublish: ((String) -> Unit)? = null

    /** Called when a client disconnects. */
    var onDisconnect: ((String) -> Unit)? = null

    /** Called for each decoded frame as raw Annex-B NALU bytes. */
    var onFrame: ((String, ByteArray) -> Unit)? = null

    private val server = RTMPServer()
    private val renderer = VideoRenderer(server)

    init {
        server.onPublish = { key ->
            renderer.onPublish(key)
            onPublish?.invoke(key)
        }
        server.onDisconnect = { key -> onDisconnect?.invoke(key) }
        server.onFrame = { key, frame -> onFrame?.invoke(key, frame) }
    }

    /** Starts the RTMP server on the specified port. */
    fun start(port: Int = 1935) {
        server.start(port)
    }

    /** Stops the RTMP server and releases resources. */
    fun stop() {
        server.stop()
        renderer.releaseAll()
    }

    fun attachSurface(streamKey: String, surfaceView: SurfaceView) {
        renderer.attachSurfaceView(streamKey, surfaceView)
    }

    fun attachSurface(streamKey: String, textureView: TextureView) {
        renderer.attachTextureView(streamKey, textureView)
    }

    fun attachSurface(streamKey: String, surface: Surface) {
        renderer.attachSurface(streamKey, surface)
    }

    fun detachSurface(streamKey: String) {
        renderer.detachSurface(streamKey)
    }
}