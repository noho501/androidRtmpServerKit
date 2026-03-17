package com.example.rtmpserverkit.media

import android.util.Log
import com.example.rtmpserverkit.utils.ByteReader

/**
 * Parses RTMP FLV video tags to extract H264 (AVC) NAL units.
 *
 * FLV Video Tag layout:
 *   Byte 0: frame type (4 bits) | codec id (4 bits)
 *   Byte 1: AVC packet type (0=sequence header, 1=NALU, 2=end)
 *   Bytes 2-4: composition time offset
 *   Bytes 5+: payload
 */
internal class H264Parser {

    companion object {
        private const val TAG = "H264Parser"
        private const val CODEC_AVC = 7
        private const val AVC_SEQUENCE_HEADER = 0
        private const val AVC_NALU = 1
        private const val AVC_END_OF_SEQUENCE = 2
    }

    private val spsPpsStore = SPSPPSStore()
    private val naluAssembler = NALUAssembler()
    private var naluLengthSize = 4

    val spsStore: SPSPPSStore get() = spsPpsStore

    /**
     * Parses raw RTMP video payload.
     * @return list of Annex-B formatted NAL units, or null if not H264 / not ready
     */
    fun parseVideoData(payload: ByteArray): List<ByteArray>? {
        if (payload.size < 5) return null

        val firstByte = ByteReader.getUInt8(payload, 0)
        val codecId = firstByte and 0x0F

        if (codecId != CODEC_AVC) return null

        val avcPacketType = ByteReader.getUInt8(payload, 1)
        // composition time at bytes 2-4 (ignored)

        return when (avcPacketType) {
            AVC_SEQUENCE_HEADER -> {
                parseAVCDecoderConfigRecord(payload, 5)
                null // Don't return NALUs for sequence header
            }
            AVC_NALU -> {
                if (!spsPpsStore.hasSPSPPS) return null
                val naluData = payload.copyOfRange(5, payload.size)
                naluAssembler.toAnnexB(naluData, naluLengthSize)
            }
            AVC_END_OF_SEQUENCE -> null
            else -> null
        }
    }

    /**
     * Parses AVCDecoderConfigurationRecord to extract SPS and PPS.
     *
     * Layout:
     *   1 byte: configurationVersion (=1)
     *   1 byte: AVCProfileIndication
     *   1 byte: profile_compatibility
     *   1 byte: AVCLevelIndication
     *   1 byte: lengthSizeMinusOne (& 0x03) + 1 = naluLengthSize
     *   1 byte: numSequenceParameterSets (& 0x1F)
     *   For each SPS:
     *     2 bytes: sequenceParameterSetLength
     *     N bytes: SPS data
     *   1 byte: numPictureParameterSets
     *   For each PPS:
     *     2 bytes: pictureParameterSetLength
     *     N bytes: PPS data
     */
    private fun parseAVCDecoderConfigRecord(data: ByteArray, startOffset: Int) {
        try {
            var offset = startOffset
            if (offset + 5 > data.size) return

            // configurationVersion, AVCProfileIndication, etc.
            offset += 4 // skip first 4 bytes

            // lengthSizeMinusOne
            naluLengthSize = (ByteReader.getUInt8(data, offset) and 0x03) + 1
            offset++

            // numSequenceParameterSets
            val numSPS = ByteReader.getUInt8(data, offset) and 0x1F
            offset++

            var spsData: ByteArray? = null
            for (i in 0 until numSPS) {
                if (offset + 2 > data.size) break
                val spsLen = ByteReader.getUInt16(data, offset)
                offset += 2
                if (offset + spsLen > data.size) break
                spsData = data.copyOfRange(offset, offset + spsLen)
                offset += spsLen
            }

            if (offset >= data.size) return

            // numPictureParameterSets
            val numPPS = ByteReader.getUInt8(data, offset)
            offset++

            var ppsData: ByteArray? = null
            for (i in 0 until numPPS) {
                if (offset + 2 > data.size) break
                val ppsLen = ByteReader.getUInt16(data, offset)
                offset += 2
                if (offset + ppsLen > data.size) break
                ppsData = data.copyOfRange(offset, offset + ppsLen)
                offset += ppsLen
            }

            if (spsData != null && ppsData != null) {
                spsPpsStore.update(spsData, ppsData)
                Log.d(TAG, "SPS/PPS extracted: SPS=${spsData.size}B, PPS=${ppsData.size}B, naluLen=$naluLengthSize")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing AVCDecoderConfigRecord: ${e.message}")
        }
    }

    fun reset() {
        spsPpsStore.reset()
        naluLengthSize = 4
    }
}
