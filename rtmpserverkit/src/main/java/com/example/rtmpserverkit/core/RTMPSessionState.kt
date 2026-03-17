package com.example.rtmpserverkit.core

/**
 * Represents the current state of an RTMP session/connection.
 */
internal enum class RTMPSessionState {
    CONNECTING,
    HANDSHAKING,
    CONNECTED,
    PUBLISHING,
    CLOSED
}
