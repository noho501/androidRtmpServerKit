package com.example.rtmpserverkit.render

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.graphics.SurfaceTexture
import com.example.rtmpserverkit.media.MediaCodecDecoder

/**
 * Manages Surface attachment for the MediaCodec decoder.
 * Supports both SurfaceView and TextureView.
 */
internal class VideoRenderer(private val decoder: MediaCodecDecoder) {

    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            decoder.attachSurface(holder.surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // Surface size changed - decoder handles this
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            decoder.attachSurface(null)
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            decoder.attachSurface(Surface(texture))
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            decoder.attachSurface(null)
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    fun attachSurfaceView(view: SurfaceView) {
        detach()
        surfaceView = view
        view.holder.addCallback(surfaceCallback)
        // If surface already exists
        if (view.holder.surface != null && view.holder.surface.isValid) {
            decoder.attachSurface(view.holder.surface)
        }
    }

    fun attachTextureView(view: TextureView) {
        detach()
        textureView = view
        view.surfaceTextureListener = textureListener
        if (view.isAvailable) {
            val texture = view.surfaceTexture ?: return
            decoder.attachSurface(Surface(texture))
        }
    }

    fun attachSurface(surface: Surface) {
        detach()
        decoder.attachSurface(surface)
    }

    fun detach() {
        surfaceView?.holder?.removeCallback(surfaceCallback)
        surfaceView = null
        textureView?.surfaceTextureListener = null
        textureView = null
    }

    fun release() {
        detach()
        decoder.attachSurface(null)
    }
}
