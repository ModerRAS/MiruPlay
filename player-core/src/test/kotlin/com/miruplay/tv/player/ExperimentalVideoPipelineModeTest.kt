package com.miruplay.tv.player

import com.miruplay.tv.model.PlaybackRenderBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalVideoPipelineModeTest {
    @Test
    fun `es2 devices use the dedicated gl surface experimental pipeline`() {
        assertEquals(
            ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE,
            resolveExperimentalVideoPipelineMode(glEsMajorVersion = 2),
        )
        assertTrue(shouldUseDedicatedExperimentalGlSurface(glEsMajorVersion = 2))
    }

    @Test
    fun `es3 devices keep the media3 video effects pipeline`() {
        assertEquals(
            ExperimentalVideoPipelineMode.MEDIA3_EFFECTS,
            resolveExperimentalVideoPipelineMode(glEsMajorVersion = 3),
        )
        assertFalse(shouldUseDedicatedExperimentalGlSurface(glEsMajorVersion = 3))
    }

    @Test
    fun `experimental video effects pipeline is only used for experimental backend on es3 devices`() {
        assertTrue(
            shouldUseExperimentalVideoEffectsPipeline(
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                glEsMajorVersion = 3,
            )
        )
        assertFalse(
            shouldUseExperimentalVideoEffectsPipeline(
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                glEsMajorVersion = 2,
            )
        )
        assertFalse(
            shouldUseExperimentalVideoEffectsPipeline(
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                glEsMajorVersion = 3,
            )
        )
    }

    @Test
    fun `dedicated gl pipeline bypasses exo video effects dispatch entirely`() {
        assertTrue(
            shouldBypassExoVideoEffectsDispatch(
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                glEsMajorVersion = 2,
            )
        )
        assertFalse(
            shouldBypassExoVideoEffectsDispatch(
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                glEsMajorVersion = 3,
            )
        )
        assertFalse(
            shouldBypassExoVideoEffectsDispatch(
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                glEsMajorVersion = 2,
            )
        )
    }

    @Test
    fun `undefined gl es version falls back to the safer dedicated surface pipeline`() {
        assertEquals(2, resolveGlEsMajorVersion(android.content.pm.ConfigurationInfo.GL_ES_VERSION_UNDEFINED))
    }
}
