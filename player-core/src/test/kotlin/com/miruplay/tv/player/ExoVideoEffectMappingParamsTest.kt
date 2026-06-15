package com.miruplay.tv.player

import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.defaultToneMappingRuleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExoVideoEffectMappingParamsTest {
    @Test
    fun `sdr passthrough yields no params`() {
        val params = resolveExoVideoEffectParams(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.SDR),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.SDR),
        )

        assertNull(params)
    }

    @Test
    fun `enabled hdr rule yields contrast and rgb scale params`() {
        val params = resolveExoVideoEffectParams(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(
                enabled = true,
                targetSdrNits = 120,
                contrastRecovery = 12,
                saturationRecovery = 10,
                highlightCompression = 18,
                curvePreset = ToneMappingCurvePreset.MOBIUS,
            ),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
        )

        assertNotNull(params)
        assertTrue(params!!.contrast > 0f)
        assertTrue(params.redScale > 1f)
        assertTrue(params.greenScale > 1f)
        assertTrue(params.blueScale > 1f)
    }

    @Test
    fun `disabled hdr rule yields no params`() {
        val params = resolveExoVideoEffectParams(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(enabled = false),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
        )

        assertNull(params)
    }

    @Test
    fun `hdr10 plus uses same param shape but stronger rgb compensation than hdr10`() {
        val hdr10 = resolveExoVideoEffectParams(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(enabled = true),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
        )
        val hdr10Plus = resolveExoVideoEffectParams(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10_PLUS).copy(enabled = true),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10_PLUS),
        )

        assertNotNull(hdr10)
        assertNotNull(hdr10Plus)
        assertTrue(hdr10Plus!!.contrast >= hdr10!!.contrast)
        assertTrue(hdr10Plus.redScale > hdr10.redScale)
        assertTrue(hdr10Plus.greenScale > hdr10.greenScale)
    }
}
