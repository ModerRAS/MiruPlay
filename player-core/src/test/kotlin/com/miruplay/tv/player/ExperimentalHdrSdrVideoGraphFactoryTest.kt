package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import com.miruplay.tv.model.PlaybackRenderBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalHdrSdrVideoGraphFactoryTest {

    @Test
    fun `hdr input is forced to sdr output for experimental graph`() {
        val hdrInput = ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .setLumaBitdepth(10)
            .setChromaBitdepth(10)
            .build()

        val resolved = resolveExperimentalGraphOutputColorInfo(
            inputColorInfo = hdrInput,
            requestedOutputColorInfo = hdrInput,
        )

        assertEquals(ColorInfo.SDR_BT709_LIMITED, resolved)
    }

    @Test
    fun `sdr input keeps requested output color info`() {
        val requestedOutput = ColorInfo.SRGB_BT709_FULL

        val resolved = resolveExperimentalGraphOutputColorInfo(
            inputColorInfo = ColorInfo.SDR_BT709_LIMITED,
            requestedOutputColorInfo = requestedOutput,
        )

        assertSame(requestedOutput, resolved)
    }

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
