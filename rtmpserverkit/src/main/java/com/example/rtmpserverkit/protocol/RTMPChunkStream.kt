package com.example.rtmpserverkit.protocol

/**
 * Represents the state of a single chunk stream.
 */
internal data class RTMPChunkStream(
    val csid: Int,
    var messageType: Int = 0,
    var messageStreamId: Long = 0,
    var timestamp: Long = 0,
    var timestampDelta: Long = 0,
    var messageLength: Int = 0,
    var payload: ByteArray = ByteArray(0),
    var bytesRead: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RTMPChunkStream) return false
        return csid == other.csid
    }

    override fun hashCode(): Int = csid
}
