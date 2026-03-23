package com.example.rtmpserverkit.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
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
    fun initWithSPSPPS(sps: ByteArray, pps: ByteArray, width: Int, height: Int) {
        val surf = surface ?: run {
            return
        }

        try {
            release()

            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                format.setInteger(MediaFormat.KEY_LATENCY, 0)
            }

            format.setInteger(MediaFormat.KEY_PRIORITY, 0) // Real-time priority
            // SPS and PPS are stored without start codes in MediaFormat
            format.setByteBuffer("csd-0", ByteBuffer.wrap(prependStartCode(sps)))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(prependStartCode(pps)))

            val newCodec = MediaCodec.createDecoderByType(MIME_TYPE)
            newCodec.configure(format, surf, null, 0)
            newCodec.start()
            codec = newCodec
            initialized.set(true)
            running.set(true)

        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to init decoder!", e)
            release()
        }
    }

    /**
     * Feed an Annex-B formatted NALU (with 0x00000001 start code) into the decoder.
     */
    fun feedNalu(annexBData: ByteArray, timestampUs: Long = System.nanoTime() / 1000) {
        val c = codec ?: return
        if (!running.get()) return

        try {
            var inputIndex = -1
            var attempts = 0
            // Try again up to 3 times if the buffer is full.
            while (inputIndex < 0 && attempts < 3 && running.get()) {
                inputIndex = c.dequeueInputBuffer(2000)
                if (inputIndex >= 0) {
                    val inputBuffer = c.getInputBuffer(inputIndex) ?: return
                    inputBuffer.clear()
                    inputBuffer.put(annexBData)
                    c.queueInputBuffer(inputIndex, 0, annexBData.size, timestampUs, 0)
                } else {
                    attempts++
                }
                // If the output buffer is full, quickly free up space to empty the input.
                drainOutput()
            }
        } catch (e: Exception) {
            Log.e(TAG, "feedNalu error: $e")
        }
    }

    private fun drainOutput() {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        // Wait a maximum of 10ms to retrieve the output buffer.
        var outputIndex = c.dequeueOutputBuffer(info, 10000)
        while (outputIndex >= 0) {
            // true: Display on screen immediately
            c.releaseOutputBuffer(outputIndex, true)
            outputIndex = c.dequeueOutputBuffer(info, 0)
        }
    }

    fun release() {
        running.set(false)
        initialized.set(false)
        try {
            codec?.stop()
        } catch (_: Exception) { /* ignore */
        }
        try {
            codec?.release()
        } catch (_: Exception) { /* ignore */
        }
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
