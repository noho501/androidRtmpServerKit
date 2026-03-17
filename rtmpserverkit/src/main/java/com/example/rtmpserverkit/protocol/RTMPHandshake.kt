package com.example.rtmpserverkit.protocol

import com.example.rtmpserverkit.utils.ByteReader
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/**
 * Implements the RTMP handshake (simple handshake).
 *
 * Sequence:
 *   Client → Server: C0 (1 byte) + C1 (1536 bytes)
 *   Server → Client: S0 (1 byte) + S1 (1536 bytes) + S2 (1536 bytes copy of C1)
 *   Client → Server: C2 (1536 bytes copy of S1)
 */
internal class RTMPHandshake {

    companion object {
        private const val HANDSHAKE_SIZE = 1536
        private const val RTMP_VERSION: Byte = 3
    }

    private val random = SecureRandom()

    /**
     * Performs the server-side handshake.
     * @return true if handshake succeeded
     */
    fun performServer(input: InputStream, output: OutputStream): Boolean {
        return try {
            // Read C0+C1
            val c0 = input.read()
            if (c0 < 0) return false
            val c1 = ByteReader.readBytes(input, HANDSHAKE_SIZE)

            // Build S1: time(4) + zeros(4) + random(1528)
            val s1 = ByteArray(HANDSHAKE_SIZE)
            val now = (System.currentTimeMillis() / 1000).toInt()
            s1[0] = (now shr 24).toByte()
            s1[1] = (now shr 16).toByte()
            s1[2] = (now shr 8).toByte()
            s1[3] = now.toByte()
            val randomPart = ByteArray(HANDSHAKE_SIZE - 8)
            random.nextBytes(randomPart)
            System.arraycopy(randomPart, 0, s1, 8, randomPart.size)

            // S2 = copy of C1
            val s2 = c1.copyOf()

            // Write S0 + S1 + S2
            output.write(RTMP_VERSION.toInt())
            output.write(s1)
            output.write(s2)
            output.flush()

            // Read C2 (we don't validate it for simplicity)
            ByteReader.readBytes(input, HANDSHAKE_SIZE)

            true
        } catch (e: Exception) {
            false
        }
    }
}
