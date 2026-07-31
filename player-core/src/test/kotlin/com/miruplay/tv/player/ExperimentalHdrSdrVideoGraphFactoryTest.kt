package com.miruplay.tv.player

import com.miruplay.tv.model.PlaybackRenderBackend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalHdrSdrVideoGraphFactoryTest {
    @Test
    fun `only experimental backend enables exo effect pipeline`() {
        assertTrue(
            shouldUseExoVideoEffectsPipeline(
                effectPipelineEnabled = true,
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                usesExperimentalEffectsPlayer = true,
            )
        )
        assertFalse(
            shouldUseExoVideoEffectsPipeline(
                effectPipelineEnabled = true,
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                usesExperimentalEffectsPlayer = true,
            )
        )
        assertFalse(
            shouldUseExoVideoEffectsPipeline(
                effectPipelineEnabled = false,
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                usesExperimentalEffectsPlayer = true,
            )
        )
        assertFalse(
            shouldUseExoVideoEffectsPipeline(
                effectPipelineEnabled = true,
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                usesExperimentalEffectsPlayer = false,
            )
        )
    }
}
