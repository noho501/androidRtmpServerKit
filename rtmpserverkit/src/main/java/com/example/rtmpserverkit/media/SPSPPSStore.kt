package com.example.rtmpserverkit.media

/**
 * Stores SPS and PPS NAL units for H264 decoder configuration.
 * Thread-safe.
 */
internal class SPSPPSStore {

    @Volatile
    private var sps: ByteArray? = null

    @Volatile
    private var pps: ByteArray? = null

    val hasSPSPPS: Boolean get() = sps != null && pps != null

    fun update(newSps: ByteArray, newPps: ByteArray) {
        sps = newSps.copyOf()
        pps = newPps.copyOf()
    }

    fun getSPS(): ByteArray? = sps?.copyOf()
    fun getPPS(): ByteArray? = pps?.copyOf()

    fun reset() {
        sps = null
        pps = null
    }
}
