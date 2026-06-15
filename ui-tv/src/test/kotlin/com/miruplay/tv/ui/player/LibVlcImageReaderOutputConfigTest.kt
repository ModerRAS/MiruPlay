package com.miruplay.tv.ui.player

import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcImageReaderOutputConfigTest {
    @Test
    fun `api 29 and above uses private gpu sampled image reader config`() {
        val config = resolveLibVlcImageReaderSurfaceConfig(Build.VERSION_CODES.TIRAMISU)

        assertEquals(ImageFormat.PRIVATE, config.imageFormat)
        assertEquals(HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE, config.usage)
        assertEquals(LibVlcImageReaderCopyStrategy.HARDWARE_BITMAP, config.copyStrategy)
    }

    @Test
    fun `api 28 falls back to rgba image reader config`() {
        val config = resolveLibVlcImageReaderSurfaceConfig(Build.VERSION_CODES.P)

        assertEquals(PixelFormat.RGBA_8888, config.imageFormat)
        assertEquals(0L, config.usage)
        assertEquals(LibVlcImageReaderCopyStrategy.RGBA_PLANES, config.copyStrategy)
    }

    @Test
    fun `api 29 and above keeps output callbacks private reader aligned to the requested decoder surface size`() {
        val size = resolveLibVlcImageReaderSurfaceSize(
            requestedWidth = 1920,
            requestedHeight = 1080,
            config = resolveLibVlcImageReaderSurfaceConfig(Build.VERSION_CODES.TIRAMISU),
        )

        assertEquals(1920, size.first)
        assertEquals(1080, size.second)
    }

    @Test
    fun `api 28 rgba fallback keeps requested decoder surface size`() {
        val size = resolveLibVlcImageReaderSurfaceSize(
            requestedWidth = 1920,
            requestedHeight = 1080,
            config = resolveLibVlcImageReaderSurfaceConfig(Build.VERSION_CODES.P),
        )

        assertEquals(1920, size.first)
        assertEquals(1080, size.second)
    }

    @Test
    fun `output callbacks attach readiness follows host layout rather than private buffer size`() {
        assertTrue(
            isLibVlcOutputCallbackAttachReady(
                surfacePresent = true,
                hostWidth = 1920,
                hostHeight = 1080,
            ),
        )
        assertFalse(
            isLibVlcOutputCallbackAttachReady(
                surfacePresent = true,
                hostWidth = 0,
                hostHeight = 1080,
            ),
        )
        assertFalse(
            isLibVlcOutputCallbackAttachReady(
                surfacePresent = false,
                hostWidth = 1920,
                hostHeight = 1080,
            ),
        )
    }
}
