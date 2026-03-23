package com.example.rtmpserverkit.media

/**
 * Stores SPS and PPS NAL units for H264 decoder configuration.
 * Thread-safe.
 */
internal class SPSPPSStore {

    @Volatile
    var width: Int = 1280
    @Volatile
    var height: Int = 720

    @Volatile
    private var sps: ByteArray? = null

    @Volatile
    private var pps: ByteArray? = null

    val hasSPSPPS: Boolean get() = sps != null && pps != null

    fun update(newSps: ByteArray, newPps: ByteArray) {
        sps = newSps.copyOf()
        pps = newPps.copyOf()
    }

    fun update(newSps: ByteArray, newPps: ByteArray, width: Int, height: Int) {
        sps = newSps.copyOf()
        pps = newPps.copyOf()
        this.width = width
        this.height = height
    }

    fun getSPS(): ByteArray? = sps?.copyOf()
    fun getPPS(): ByteArray? = pps?.copyOf()

    fun reset() {
        sps = null
        pps = null
    }
}
