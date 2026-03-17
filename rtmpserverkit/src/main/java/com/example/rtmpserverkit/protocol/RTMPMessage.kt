package com.example.rtmpserverkit.protocol

/**
 * RTMP message types and data container.
 */
internal data class RTMPMessage(
    val type: Int,
    val streamId: Long,
    val timestamp: Long,
    val payload: ByteArray
) {
    companion object {
        // Message type IDs
        const val TYPE_SET_CHUNK_SIZE = 1
        const val TYPE_ABORT = 2
        const val TYPE_ACK = 3
        const val TYPE_CONTROL = 4
        const val TYPE_WINDOW_ACK_SIZE = 5
        const val TYPE_SET_PEER_BANDWIDTH = 6
        const val TYPE_AUDIO = 8
        const val TYPE_VIDEO = 9
        const val TYPE_DATA_AMF3 = 15
        const val TYPE_SHARED_OBJECT_AMF3 = 16
        const val TYPE_COMMAND_AMF3 = 17
        const val TYPE_DATA_AMF0 = 18
        const val TYPE_SHARED_OBJECT_AMF0 = 19
        const val TYPE_COMMAND_AMF0 = 20
        const val TYPE_AGGREGATE = 22
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RTMPMessage) return false
        return type == other.type &&
                streamId == other.streamId &&
                timestamp == other.timestamp &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + streamId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
