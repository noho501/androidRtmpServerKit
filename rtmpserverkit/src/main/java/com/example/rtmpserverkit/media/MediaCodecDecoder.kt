package com.example.rtmpserverkit.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decodes H264 video using MediaCodec and renders to a Surface.
 * The decoder is initialized after SPS/PPS are received.
 */
internal class MediaCodecDecoder {

    companion object {
        private const val TAG = "MediaCodecDecoder"
        private const val MIME_TYPE = "video/avc"
        private const val TIMEOUT_US = 10_000L
    }

    @Volatile
    private var codec: MediaCodec? = null

    @Volatile
    private var surface: Surface? = null

    private val initialized = AtomicBoolean(false)
    private val running = AtomicBoolean(false)

    fun attachSurface(surface: Surface?) {
        this.surface = surface
        // If codec is already running with old surface, reinitialize
        if (initialized.get() && surface != null) {
            try {
                codec?.setOutputSurface(surface)
            } catch (e: Exception) {
                // If setOutputSurface not supported, release and reinitialize on next SPS
                Log.w(TAG, "setOutputSurface failed, will reinitialize: ${e.message}")
                release()
            }
        }
    }

    /**
     * Initialize the decoder with SPS and PPS NAL unit data (without start codes).
     */
    fun initWithSPSPPS(sps: ByteArray, pps: ByteArray) {
        val surf = surface ?: run {
            Log.w(TAG, "No surface attached, skipping decoder init")
            return
        }

        try {
            release()

            val format = MediaFormat.createVideoFormat(MIME_TYPE, 0, 0)
            // SPS and PPS are stored without start codes in MediaFormat
            format.setByteBuffer("csd-0", ByteBuffer.wrap(prependStartCode(sps)))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(prependStartCode(pps)))

            val newCodec = MediaCodec.createDecoderByType(MIME_TYPE)
            newCodec.configure(format, surf, null, 0)
            newCodec.start()
            codec = newCodec
            initialized.set(true)
            running.set(true)
            Log.i(TAG, "MediaCodec decoder initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init decoder: ${e.message}")
            release()
        }
    }

    /**
     * Feed an Annex-B formatted NALU (with 0x00000001 start code) into the decoder.
     */
    fun feedNalu(annexBData: ByteArray) {
        val c = codec ?: return
        if (!running.get()) return

        try {
            val inputIndex = c.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = c.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()
                val toWrite = minOf(annexBData.size, inputBuffer.remaining())
                inputBuffer.put(annexBData, 0, toWrite)
                c.queueInputBuffer(inputIndex, 0, toWrite, System.nanoTime() / 1000, 0)
            }

            // Drain output
            val info = MediaCodec.BufferInfo()
            var outputIndex = c.dequeueOutputBuffer(info, 0)
            while (outputIndex >= 0) {
                val render = info.size > 0
                c.releaseOutputBuffer(outputIndex, render)
                outputIndex = c.dequeueOutputBuffer(info, 0)
            }
        } catch (e: MediaCodec.CodecException) {
            Log.e(TAG, "Codec error: ${e.message}")
            running.set(false)
            release()
        } catch (e: Exception) {
            Log.w(TAG, "feedNalu error: ${e.message}")
        }
    }

    fun release() {
        running.set(false)
        initialized.set(false)
        try {
            codec?.stop()
        } catch (e: Exception) { /* ignore */ }
        try {
            codec?.release()
        } catch (e: Exception) { /* ignore */ }
        codec = null
    }

    private fun prependStartCode(nalu: ByteArray): ByteArray {
        val result = ByteArray(4 + nalu.size)
        result[0] = 0x00
        result[1] = 0x00
        result[2] = 0x00
        result[3] = 0x01
        System.arraycopy(nalu, 0, result, 4, nalu.size)
        return result
    }
}
