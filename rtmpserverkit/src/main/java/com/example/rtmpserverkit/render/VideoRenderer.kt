package com.example.rtmpserverkit.render

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.graphics.SurfaceTexture
import com.example.rtmpserverkit.core.RTMPServer
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Surface attachment for the MediaCodec decoder.
 * Supports handling multiple streams by streamKey.
 */
internal class VideoRenderer(private val server: RTMPServer) {

    private val surfaceMap = ConcurrentHashMap<String, Surface>()

    fun onPublish(streamKey: String) {
        // Attach again if surface was mapped before the stream was published
        surfaceMap[streamKey]?.let {
            server.getDecoder(streamKey)?.attachSurface(it)
        }
    }

    fun attachSurface(streamKey: String, surface: Surface) {
        surfaceMap[streamKey] = surface
        server.getDecoder(streamKey)?.attachSurface(surface)
    }

    fun detachSurface(streamKey: String) {
        surfaceMap.remove(streamKey)
        server.getDecoder(streamKey)?.attachSurface(null)
    }

    fun attachSurfaceView(streamKey: String, view: SurfaceView) {
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                attachSurface(streamKey, holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                detachSurface(streamKey)
            }
        })
        if (view.holder.surface?.isValid == true) {
            attachSurface(streamKey, view.holder.surface)
        }
    }

    fun attachTextureView(streamKey: String, view: TextureView) {
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(tex: SurfaceTexture, w: Int, h: Int) {
                attachSurface(streamKey, Surface(tex))
            }
            override fun onSurfaceTextureSizeChanged(tex: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(tex: SurfaceTexture): Boolean {
                detachSurface(streamKey)
                return true
            }
            override fun onSurfaceTextureUpdated(tex: SurfaceTexture) {}
        }
        if (view.isAvailable) {
            val texture = view.surfaceTexture ?: return
            attachSurface(streamKey, Surface(texture))
        }
    }

    fun releaseAll() {
        surfaceMap.keys.forEach { detachSurface(it) }
        surfaceMap.clear()
    }
}