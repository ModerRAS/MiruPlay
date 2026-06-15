package com.miruplay.tv.ui.player

import com.miruplay.tv.player.LibVlcVmemStreamState
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LibVlcVmemPixelMappingTest {
    @Test
    fun `resolveVmemFramePipeline routes hdr planar formats through raw yuv shader path`() {
        val state = LibVlcVmemStreamState(
            configured = true,
            chroma = "I0AL",
            width = 1920,
            height = 1088,
            visibleWidth = 1920,
            visibleHeight = 1088,
            planeCount = 3,
            pitch = 3840,
            pitch1 = 1920,
            pitch2 = 1920,
            line0 = 1088,
            line1 = 544,
            line2 = 544,
            totalBytes = 6_266_880,
        )

        assertEquals(VmemFramePipeline.RAW_YUV_SHADER, resolveVmemFramePipeline(state))
        assertEquals(
            listOf(
                VmemPlaneUploadSpec(
                    planeIndex = 0,
                    textureWidth = 1920,
                    textureHeight = 1088,
                    bufferOffset = 0,
                    byteCount = 4_177_920,
                ),
                VmemPlaneUploadSpec(
                    planeIndex = 1,
                    textureWidth = 960,
                    textureHeight = 544,
                    bufferOffset = 4_177_920,
                    byteCount = 1_044_480,
                ),
                VmemPlaneUploadSpec(
                    planeIndex = 2,
                    textureWidth = 960,
                    textureHeight = 544,
                    bufferOffset = 5_222_400,
                    byteCount = 1_044_480,
                ),
            ),
            resolvePlanarHdrPlaneUploadSpecs(state),
        )
    }

    @Test
    fun `resolveVmemFramePipeline keeps rv32 on rgba upload path`() {
        val state = LibVlcVmemStreamState(
            configured = true,
            chroma = "RV32",
            width = 1920,
            height = 1080,
            visibleWidth = 1920,
            visibleHeight = 1080,
            planeCount = 1,
            pitch = 7680,
            line0 = 1080,
            totalBytes = 8_294_400,
        )

        assertEquals(VmemFramePipeline.RGBA_TEXTURE, resolveVmemFramePipeline(state))
        assertTrue(resolvePlanarHdrPlaneUploadSpecs(state).isEmpty())
    }

    @Test
    fun `repackLibVlcRv32ToRgba converts libvlc rv32 xrgb bytes into opaque rgba`() {
        val source = ByteBuffer.wrap(
            byteArrayOf(
                0x11,
                0x22,
                0x33,
                0x7F,
                0x44,
                0x55,
                0x66,
                0x01,
            ),
        )
        val target = ByteBuffer.allocate(8)

        repackLibVlcRv32ToRgba(
            source = source,
            target = target,
            width = 2,
            height = 1,
            pitch = 8,
        )

        assertArrayEquals(
            byteArrayOf(
                0x11,
                0x22,
                0x33,
                0xFF.toByte(),
                0x44,
                0x55,
                0x66,
                0xFF.toByte(),
            ),
            target.array(),
        )
    }

    @Test
    fun `repackLibVlcRv32ToRgba skips per-row padding while converting libvlc rv32 rows`() {
        val source = ByteBuffer.wrap(
            byteArrayOf(
                0x10,
                0x20,
                0x30,
                0x55,
                0x00,
                0x00,
                0x00,
                0x00,
                0x40,
                0x50,
                0x60,
                0x66,
                0x00,
                0x00,
                0x00,
                0x00,
            ),
        )
        val target = ByteBuffer.allocate(8)

        repackLibVlcRv32ToRgba(
            source = source,
            target = target,
            width = 1,
            height = 2,
            pitch = 8,
        )

        assertArrayEquals(
            byteArrayOf(
                0x10,
                0x20,
                0x30,
                0xFF.toByte(),
                0x40,
                0x50,
                0x60,
                0xFF.toByte(),
            ),
            target.array(),
        )
    }

    @Test
    fun `describeVmemPixels renders packed rgba bytes for diagnostics`() {
        val buffer = ByteBuffer.wrap(
            byteArrayOf(
                0x01,
                0x02,
                0x03,
                0x04,
                0x10,
                0x20,
                0x30,
                0x40,
            ),
        )

        val description = describeVmemPixels(buffer, pixelCount = 2)

        assertEquals("p0=[1,2,3,4] p1=[16,32,48,64]", description)
    }

    @Test
    fun `describeRawVmemBytes renders raw libvlc bytes with derived xrgb interpretation`() {
        val buffer = ByteBuffer.wrap(
            byteArrayOf(
                0x11,
                0x22,
                0x33,
                0x44,
            ),
        )

        val description = describeRawVmemBytes(buffer, pixelCount = 1)

        assertEquals("p0=[17,34,51,68] asXrgb->rgba=[17,34,51,255] x=68", description)
    }

    @Test
    fun `convertLibVlcFrameToRgba preserves rv32 path`() {
        val source = ByteBuffer.wrap(
            byteArrayOf(
                0x11,
                0x22,
                0x33,
                0x44,
            ),
        )
        val target = ByteBuffer.allocate(4)

        convertLibVlcFrameToRgba(
            source = source,
            target = target,
            state = LibVlcVmemStreamState(
                configured = true,
                chroma = "RV32",
                width = 1,
                height = 1,
                visibleWidth = 1,
                visibleHeight = 1,
                planeCount = 1,
                pitch = 4,
                line0 = 1,
                totalBytes = 4,
            ),
        )

        assertArrayEquals(
            byteArrayOf(
                0x11,
                0x22,
                0x33,
                0xFF.toByte(),
            ),
            target.array(),
        )
    }

    @Test
    fun `convertLibVlcFrameToRgba tone maps i0al hdr planes into visible rgba`() {
        val yPlane = byteArrayOf(
            0xAC.toByte(), 0x03,
            0xAC.toByte(), 0x03,
            0xAC.toByte(), 0x03,
            0xAC.toByte(), 0x03,
        )
        val uPlane = byteArrayOf(
            0x00, 0x02,
        )
        val vPlane = byteArrayOf(
            0x00, 0x02,
        )
        val source = ByteBuffer.wrap(yPlane + uPlane + vPlane)
        val target = ByteBuffer.allocate(16)

        convertLibVlcFrameToRgba(
            source = source,
            target = target,
            state = LibVlcVmemStreamState(
                configured = true,
                chroma = "I0AL",
                width = 2,
                height = 2,
                visibleWidth = 2,
                visibleHeight = 2,
                planeCount = 3,
                pitch = 4,
                pitch1 = 2,
                pitch2 = 2,
                line0 = 2,
                line1 = 1,
                line2 = 1,
                totalBytes = 12,
            ),
        )

        val pixels = target.array().toList().chunked(4)
        assertEquals(4, pixels.size)
        pixels.forEach { pixel ->
            assertEquals(0xFF.toByte(), pixel[3])
            assertTrue((pixel[0].toInt() and 0xFF) > 0)
            assertTrue((pixel[1].toInt() and 0xFF) > 0)
            assertTrue((pixel[2].toInt() and 0xFF) > 0)
        }
    }

    @Test
    fun `toneMapYuvToRgba lifts bright hdr backgrounds instead of leaving them dull`() {
        val rgba = convertSingleHdrPixelToRgba(
            yCode = 538,
            uCode = 497,
            vCode = 519,
        )

        val red = rgba[0].toInt() and 0xFF
        val green = rgba[1].toInt() and 0xFF
        val blue = rgba[2].toInt() and 0xFF

        assertTrue("expected a lifted HDR highlight, got r=$red", red >= 205)
        assertTrue("expected a lifted HDR highlight, got g=$green", green >= 180)
        assertTrue("expected a warm highlight, got b=$blue", blue >= 150)
        assertTrue("expected warm ordering r>g>b, got [$red,$green,$blue]", red > green && green > blue)
    }

    @Test
    fun `toneMapYuvToRgba preserves strong red accents in hdr content`() {
        val rgba = convertSingleHdrPixelToRgba(
            yCode = 353,
            uCode = 489,
            vCode = 566,
        )

        val red = rgba[0].toInt() and 0xFF
        val green = rgba[1].toInt() and 0xFF
        val blue = rgba[2].toInt() and 0xFF

        assertTrue("expected a visible red accent, got r=$red", red >= 170)
        assertTrue("expected red to stay clearly ahead of green, got [$red,$green,$blue]", red - green >= 60)
        assertTrue("expected red to stay clearly ahead of blue, got [$red,$green,$blue]", red - blue >= 60)
        assertTrue("expected red accent to avoid highlight washout, got g=$green", green <= 110)
        assertTrue("expected red accent to avoid highlight washout, got b=$blue", blue <= 110)
    }

    @Test
    fun `shouldQueueVmemCaptureRequest suppresses duplicate labels`() {
        assertTrue(shouldQueueVmemCaptureRequest(null, null, "cap1"))
        assertTrue(shouldQueueVmemCaptureRequest("cap0", null, "cap1"))
        assertEquals(false, shouldQueueVmemCaptureRequest("cap1", null, "cap1"))
        assertEquals(false, shouldQueueVmemCaptureRequest(null, "cap1", "cap1"))
    }

    @Test
    fun `shouldPollLatestVmemFrame prioritizes pending capture and throttles uploads`() {
        assertEquals(false, shouldPollLatestVmemFrame(true, "cap1", nowMs = 1000L, lastUploadMs = 980L, minUploadIntervalMs = 66L))
        assertEquals(false, shouldPollLatestVmemFrame(true, null, nowMs = 1000L, lastUploadMs = 980L, minUploadIntervalMs = 66L))
        assertTrue(shouldPollLatestVmemFrame(true, null, nowMs = 1100L, lastUploadMs = 1000L, minUploadIntervalMs = 66L))
        assertTrue(shouldPollLatestVmemFrame(false, null, nowMs = 1000L, lastUploadMs = 990L, minUploadIntervalMs = 66L))
    }

    @Test
    fun `flipRgbaRows vertically flips packed pixels without per pixel mutation`() {
        val source = byteArrayOf(
            0x01, 0x02, 0x03, 0x04,
            0x05, 0x06, 0x07, 0x08,
            0x11, 0x12, 0x13, 0x14,
            0x15, 0x16, 0x17, 0x18,
        )
        val target = ByteArray(source.size)

        flipRgbaRows(
            source = source,
            target = target,
            width = 2,
            height = 2,
        )

        assertArrayEquals(
            byteArrayOf(
                0x11, 0x12, 0x13, 0x14,
                0x15, 0x16, 0x17, 0x18,
                0x01, 0x02, 0x03, 0x04,
                0x05, 0x06, 0x07, 0x08,
            ),
            target,
        )
    }

    private fun convertSingleHdrPixelToRgba(
        yCode: Int,
        uCode: Int,
        vCode: Int,
    ): ByteArray {
        val source = ByteBuffer.wrap(
            byteArrayOf(
                yCode.toByte(), (yCode ushr 8).toByte(),
                yCode.toByte(), (yCode ushr 8).toByte(),
                yCode.toByte(), (yCode ushr 8).toByte(),
                yCode.toByte(), (yCode ushr 8).toByte(),
                uCode.toByte(), (uCode ushr 8).toByte(),
                vCode.toByte(), (vCode ushr 8).toByte(),
            ),
        )
        val target = ByteBuffer.allocate(16)

        convertLibVlcFrameToRgba(
            source = source,
            target = target,
            state = LibVlcVmemStreamState(
                configured = true,
                chroma = "I0AL",
                width = 2,
                height = 2,
                visibleWidth = 2,
                visibleHeight = 2,
                planeCount = 3,
                pitch = 4,
                pitch1 = 2,
                pitch2 = 2,
                line0 = 2,
                line1 = 1,
                line2 = 1,
                totalBytes = 12,
            ),
        )

        return target.array().copyOfRange(0, 4)
    }
}
