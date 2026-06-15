package com.miruplay.tv.ui.player

import android.view.Surface
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GLVideoSurfaceViewAttachmentPolicyTest {
    @Test
    fun `decoder surface is not ready before minimum dimensions are reached`() {
        val surface = mockk<Surface>()

        assertFalse(isDecoderVideoSurfaceReady(surface, width = 1, height = 1080))
        assertFalse(isDecoderVideoSurfaceReady(surface, width = 1920, height = 1))
        assertFalse(isDecoderVideoSurfaceReady(surface, width = 3, height = 3))
    }

    @Test
    fun `decoder surface becomes ready once width and height reach minimum size`() {
        val surface = mockk<Surface>()

        assertTrue(isDecoderVideoSurfaceReady(surface, width = 4, height = 4))
        assertTrue(isDecoderVideoSurfaceReady(surface, width = 1920, height = 1080))
    }

    @Test
    fun `decoder surface is not ready without a surface instance`() {
        assertFalse(isDecoderVideoSurfaceReady(surface = null, width = 1920, height = 1080))
    }
}
