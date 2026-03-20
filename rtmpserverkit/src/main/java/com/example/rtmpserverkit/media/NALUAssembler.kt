package com.example.rtmpserverkit.media

import com.example.rtmpserverkit.utils.ByteReader

/**
 * Assembles RTMP length-prefixed NALUs into Annex-B format.
 */
internal class NALUAssembler {

    companion object {
        val ANNEX_B_START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)
    }

    /**
     * Converts RTMP length-prefixed NALU format to Annex-B.
     * Returns list of Annex-B formatted NAL units.
     *
     * @param data raw NALU data (length-prefixed, 4-byte big-endian lengths)
     * @param naluLengthSize bytes used for length prefix (usually 4)
     */
    fun toAnnexB(data: ByteArray, naluLengthSize: Int = 4): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var offset = 0

        while (offset + naluLengthSize <= data.size) {
            val naluLen = when (naluLengthSize) {
                4 -> ByteReader.getUInt32(data, offset).toInt()
                3 -> ByteReader.getUInt24(data, offset)
                2 -> ByteReader.getUInt16(data, offset)
                1 -> ByteReader.getUInt8(data, offset)
                else -> break
            }
            offset += naluLengthSize

            if (naluLen <= 0 || offset + naluLen > data.size) break

            val nalu = ByteArray(4 + naluLen)
            System.arraycopy(ANNEX_B_START_CODE, 0, nalu, 0, 4)
            System.arraycopy(data, offset, nalu, 4, naluLen)
            result.add(nalu)

            offset += naluLen
        }

        return result
    }

    /**
     * Wraps raw NALU bytes (without start code) in Annex-B format.
     */
    fun wrapWithStartCode(naluData: ByteArray): ByteArray {
        val result = ByteArray(4 + naluData.size)
        System.arraycopy(ANNEX_B_START_CODE, 0, result, 0, 4)
        System.arraycopy(naluData, 0, result, 4, naluData.size)
        return result
    }

    /**
     * Returns true if the NAL unit is an IDR (keyframe).
     */
    fun isIDR(annexBNalu: ByteArray): Boolean {
        val startOffset = if (annexBNalu.size > 4 &&
            annexBNalu[0] == 0.toByte() && annexBNalu[1] == 0.toByte() &&
            annexBNalu[2] == 0.toByte() && annexBNalu[3] == 1.toByte()) 4 else 0
        if (startOffset >= annexBNalu.size) return false
        val naluType = annexBNalu[startOffset].toInt() and 0x1F
        return naluType == 5 // IDR slice
    }
}
