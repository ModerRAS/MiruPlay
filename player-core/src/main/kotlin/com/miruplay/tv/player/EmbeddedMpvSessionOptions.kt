package com.miruplay.tv.player

import com.miruplay.tv.model.PeakDetectionStrategy
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.ToneMappingRuleSet
import `is`.xyz.mpv.MiruMpvSurfaceView

internal fun buildEmbeddedMpvSessionOptions(
    ruleSet: ToneMappingRuleSet,
    shaderPaths: List<String>,
    speed: Float = 1.0f,
): MiruMpvSurfaceView.SessionOptions {
    val peakDetection = resolveEmbeddedMpvPeakDetection(ruleSet.peakDetectionStrategy)
    return MiruMpvSurfaceView.SessionOptions(
        vo = "gpu-next",
        hwdec = "mediacodec-copy",
        profile = "fast",
        targetPrim = if (ruleSet.enabled) "bt.709" else null,
        targetTrc = if (ruleSet.enabled) "bt.1886" else null,
        targetPeak = if (ruleSet.enabled) ruleSet.targetSdrNits else null,
        hdrReferenceWhite = if (ruleSet.enabled) ruleSet.targetSdrNits.coerceIn(80, 203) else null,
        toneMapping = resolveEmbeddedMpvToneMapping(ruleSet),
        toneMappingParam = if (ruleSet.enabled) resolveEmbeddedMpvToneMappingParam(ruleSet) else null,
        hdrComputePeak = if (ruleSet.enabled) peakDetection.enabled else null,
        hdrPeakPercentile = if (ruleSet.enabled) peakDetection.peakPercentile else null,
        hdrPeakDecayRate = if (ruleSet.enabled) peakDetection.peakDecayRate else null,
        hdrSceneThresholdLow = if (ruleSet.enabled) peakDetection.sceneThresholdLow else null,
        hdrSceneThresholdHigh = if (ruleSet.enabled) peakDetection.sceneThresholdHigh else null,
        hdrContrastRecovery = if (ruleSet.enabled) resolveEmbeddedMpvContrastRecovery(ruleSet) else null,
        saturation = if (ruleSet.enabled) resolveEmbeddedMpvSaturation(ruleSet) else null,
        gamutMappingMode = if (ruleSet.enabled) "perceptual" else null,
        deband = ruleSet.enabled,
        shaderPaths = shaderPaths,
        extraOptions = mapOf(
            "speed" to speed.coerceIn(0.25f, 3.0f).toString(),
            "keep-open" to "yes",
            "osc" to "no",
            "input-default-bindings" to "yes",
        ),
    )
}

private fun resolveEmbeddedMpvToneMapping(ruleSet: ToneMappingRuleSet): String? =
    when {
        !ruleSet.enabled -> null
        ruleSet.curvePreset == ToneMappingCurvePreset.PASSTHROUGH -> "clip"
        ruleSet.curvePreset == ToneMappingCurvePreset.MOBIUS -> "mobius"
        ruleSet.curvePreset == ToneMappingCurvePreset.REINHARD -> "reinhard"
        else -> null
    }

private fun resolveEmbeddedMpvToneMappingParam(ruleSet: ToneMappingRuleSet): Float? =
    when (ruleSet.curvePreset) {
        ToneMappingCurvePreset.PASSTHROUGH -> null
        ToneMappingCurvePreset.MOBIUS -> (0.2f + ruleSet.highlightCompression / 180f).coerceIn(0.2f, 0.85f)
        ToneMappingCurvePreset.REINHARD -> (0.7f - ruleSet.highlightCompression / 250f).coerceIn(0.25f, 0.8f)
    }

private fun resolveEmbeddedMpvContrastRecovery(ruleSet: ToneMappingRuleSet): Float =
    (ruleSet.contrastRecovery / 40f).coerceIn(0f, 2f)

private fun resolveEmbeddedMpvSaturation(ruleSet: ToneMappingRuleSet): Float =
    (ruleSet.saturationRecovery * 2f).coerceIn(-100f, 100f)

private fun resolveEmbeddedMpvPeakDetection(strategy: PeakDetectionStrategy): EmbeddedMpvPeakDetection =
    when (strategy) {
        PeakDetectionStrategy.DISABLED,
        PeakDetectionStrategy.STATIC_METADATA -> EmbeddedMpvPeakDetection(enabled = false)
        PeakDetectionStrategy.DYNAMIC -> EmbeddedMpvPeakDetection(
            enabled = true,
            peakPercentile = 100f,
            peakDecayRate = 20f,
            sceneThresholdLow = 1f,
            sceneThresholdHigh = 3f,
        )
        PeakDetectionStrategy.DYNAMIC_AGGRESSIVE -> EmbeddedMpvPeakDetection(
            enabled = true,
            peakPercentile = 99.9f,
            peakDecayRate = 12f,
            sceneThresholdLow = 0.5f,
            sceneThresholdHigh = 2f,
        )
    }

private data class EmbeddedMpvPeakDetection(
    val enabled: Boolean,
    val peakPercentile: Float? = null,
    val peakDecayRate: Float? = null,
    val sceneThresholdLow: Float? = null,
    val sceneThresholdHigh: Float? = null,
)
