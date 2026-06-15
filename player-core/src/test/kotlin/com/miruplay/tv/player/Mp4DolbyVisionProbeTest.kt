package com.miruplay.tv.player

import com.miruplay.tv.model.DolbyVisionProfile
import com.miruplay.tv.model.VideoSignalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Mp4DolbyVisionProbeTest {
    @Test
    fun `parser extracts profile 5 from dvcC payload`() {
        val descriptor = parseMp4DolbyVisionDescriptor(
            byteArrayOf(0, 0, 0, 0) + buildDvConfigurationBox(
                boxType = "dvcC",
                profile = 5,
                level = 6,
                compatibilityId = 0,
            )
        )

        assertNotNull(descriptor)
        assertEquals(VideoSignalKind.DOLBY_VISION, descriptor!!.signalKind)
        assertEquals(DolbyVisionProfile.PROFILE_5, descriptor.dolbyVisionProfile)
        assertEquals("06", descriptor.dolbyVisionLevel)
        assertEquals("dvhe.05.06", descriptor.codecId)
    }

    @Test
    fun `parser distinguishes profile 8 dot 4 using compatibility id`() {
        val descriptor = parseMp4DolbyVisionDescriptor(
            buildDvConfigurationBox(
                boxType = "dvcC",
                profile = 8,
                level = 7,
                compatibilityId = 4,
            )
        )

        assertNotNull(descriptor)
        assertEquals(DolbyVisionProfile.PROFILE_8_4, descriptor!!.dolbyVisionProfile)
        assertEquals("07", descriptor.dolbyVisionLevel)
    }

    @Test
    fun `parser skips oversized non matching boxes without overflowing`() {
        val firstBoxSize = 100
        val bytes = ByteArray(160).also { payload ->
            payload[0] = 0
            payload[1] = 0
            payload[2] = 0
            payload[3] = firstBoxSize.toByte()
            "free".encodeToByteArray().copyInto(payload, destinationOffset = 4)

            val overflowOffset = firstBoxSize
            payload[overflowOffset] = 0x7F
            payload[overflowOffset + 1] = 0xFF.toByte()
            payload[overflowOffset + 2] = 0xFF.toByte()
            payload[overflowOffset + 3] = 0xF0.toByte()
            "skip".encodeToByteArray().copyInto(payload, destinationOffset = overflowOffset + 4)

            buildDvConfigurationBox(
                boxType = "dvcC",
                profile = 5,
                level = 6,
                compatibilityId = 0,
            ).copyInto(payload, destinationOffset = 120)
        }

        val descriptor = parseMp4DolbyVisionDescriptor(bytes)

        assertNotNull(descriptor)
        assertEquals(DolbyVisionProfile.PROFILE_5, descriptor!!.dolbyVisionProfile)
    }

    @Test
    fun `additional probe offsets jump past leading mdat to next root box`() {
        val mdatHeaderOffset = 40L
        val mdatDeclaredSize = 4_137_148_396L
        val bytes = buildBox("ftyp", ByteArray(24)) +
            buildBox("free", ByteArray(0)) +
            buildBoxWithDeclaredSize("mdat", mdatDeclaredSize, ByteArray(64))

        val offsets = findAdditionalMp4ProbeOffsets(
            bytes = bytes,
            probeStartOffset = 0L,
        )

        assertEquals(listOf(mdatHeaderOffset + mdatDeclaredSize), offsets)
    }

    @Test
    fun `parser finds dolby vision nested inside partial moov window`() {
        val descriptor = parseMp4DolbyVisionDescriptor(
            buildPartialMoovWindow(
                buildSampleEntryBox(
                    sampleEntryType = "hev1",
                    childBoxes = buildDvConfigurationBox(
                        boxType = "dvcC",
                        profile = 5,
                        level = 6,
                        compatibilityId = 0,
                    ),
                )
            )
        )

        assertNotNull(descriptor)
        assertEquals(VideoSignalKind.DOLBY_VISION, descriptor!!.signalKind)
        assertEquals(DolbyVisionProfile.PROFILE_5, descriptor.dolbyVisionProfile)
        assertEquals("06", descriptor.dolbyVisionLevel)
    }

    private fun buildDvConfigurationBox(
        boxType: String,
        profile: Int,
        level: Int,
        compatibilityId: Int,
    ): ByteArray {
        val payload = ByteArray(24)
        payload[0] = 1
        payload[1] = 0
        val packed = ((profile and 0x7F) shl 9) or
            ((level and 0x3F) shl 3) or
            (1 shl 2) or
            1
        payload[2] = ((packed ushr 8) and 0xFF).toByte()
        payload[3] = (packed and 0xFF).toByte()
        payload[4] = ((compatibilityId and 0x0F) shl 4).toByte()

        val size = payload.size + 8
        return ByteArray(size).also { bytes ->
            bytes[0] = ((size ushr 24) and 0xFF).toByte()
            bytes[1] = ((size ushr 16) and 0xFF).toByte()
            bytes[2] = ((size ushr 8) and 0xFF).toByte()
            bytes[3] = (size and 0xFF).toByte()
            boxType.encodeToByteArray().copyInto(bytes, destinationOffset = 4)
            payload.copyInto(bytes, destinationOffset = 8)
        }
    }

    private fun buildBox(
        boxType: String,
        payload: ByteArray,
    ): ByteArray =
        buildBoxWithDeclaredSize(
            boxType = boxType,
            declaredSize = (payload.size + 8).toLong(),
            payload = payload,
        )

    private fun buildBoxWithDeclaredSize(
        boxType: String,
        declaredSize: Long,
        payload: ByteArray,
    ): ByteArray {
        require(declaredSize >= 8)
        return ByteArray(payload.size + 8).also { bytes ->
            bytes[0] = ((declaredSize ushr 24) and 0xFF).toByte()
            bytes[1] = ((declaredSize ushr 16) and 0xFF).toByte()
            bytes[2] = ((declaredSize ushr 8) and 0xFF).toByte()
            bytes[3] = (declaredSize and 0xFF).toByte()
            boxType.encodeToByteArray().copyInto(bytes, destinationOffset = 4)
            payload.copyInto(bytes, destinationOffset = 8)
        }
    }

    private fun buildPartialMoovWindow(sampleEntryBox: ByteArray): ByteArray {
        val stsdPayload = ByteArray(4) +
            byteArrayOf(0, 0, 0, 1) +
            sampleEntryBox
        val stsd = buildBox("stsd", stsdPayload)
        val stbl = buildBox("stbl", stsd)
        val minf = buildBox("minf", stbl)
        val mdia = buildBox("mdia", minf)
        val trak = buildBox("trak", mdia)
        val mvhd = buildBox("mvhd", ByteArray(16))
        val moovPayload = mvhd + trak
        return buildBoxWithDeclaredSize(
            boxType = "moov",
            declaredSize = moovPayload.size.toLong() + 8L + 1024L,
            payload = moovPayload,
        )
    }

    private fun buildSampleEntryBox(
        sampleEntryType: String,
        childBoxes: ByteArray,
    ): ByteArray {
        val visualSampleEntryFields = ByteArray(78)
        return buildBox(
            boxType = sampleEntryType,
            payload = visualSampleEntryFields + childBoxes,
        )
    }
}
