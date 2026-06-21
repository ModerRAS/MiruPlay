package com.miruplay.tv.player

import android.os.Build
import android.os.Process
import com.miruplay.tv.model.PeakDetectionStrategy
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.ToneMappingRuleSet
import `is`.xyz.mpv.MiruMpvSurfaceView

fun buildEmbeddedMpvSessionOptions(
    ruleSet: ToneMappingRuleSet,
    shaderPaths: List<String>,
    speed: Float = 1.0f,
    runtimeAbiIs32Bit: Boolean = isEmbeddedMpvRuntime32Bit(),
    debugConfig: EmbeddedMpvDebugConfig = EmbeddedMpvDebugConfig(),
): MiruMpvSurfaceView.SessionOptions {
    val peakDetection = resolveEmbeddedMpvPeakDetection(
        strategy = effectiveEmbeddedMpvPeakDetectionStrategy(
            strategy = ruleSet.peakDetectionStrategy,
            runtimeAbiIs32Bit = runtimeAbiIs32Bit,
        ),
    )
    val toneMappingEnabled = ruleSet.enabled
    return MiruMpvSurfaceView.SessionOptions(
        vo = effectiveEmbeddedMpvVideoOutput(
            runtimeAbiIs32Bit = runtimeAbiIs32Bit,
            debugConfig = debugConfig,
        ),
        hwdec = effectiveEmbeddedMpvHwdec(debugConfig),
        profile = "fast",
        targetPrim = if (toneMappingEnabled) "bt.709" else null,
        targetTrc = if (toneMappingEnabled) "bt.1886" else null,
        targetPeak = if (toneMappingEnabled) ruleSet.targetSdrNits else null,
        hdrReferenceWhite = if (toneMappingEnabled) ruleSet.targetSdrNits.coerceIn(80, 203) else null,
        toneMapping = resolveEmbeddedMpvToneMapping(ruleSet),
        toneMappingParam = if (toneMappingEnabled) resolveEmbeddedMpvToneMappingParam(ruleSet) else null,
        hdrComputePeak = if (toneMappingEnabled) peakDetection.enabled else null,
        hdrPeakPercentile = if (toneMappingEnabled) peakDetection.peakPercentile else null,
        hdrPeakDecayRate = if (toneMappingEnabled) peakDetection.peakDecayRate else null,
        hdrSceneThresholdLow = if (toneMappingEnabled) peakDetection.sceneThresholdLow else null,
        hdrSceneThresholdHigh = if (toneMappingEnabled) peakDetection.sceneThresholdHigh else null,
        hdrContrastRecovery = if (toneMappingEnabled) resolveEmbeddedMpvContrastRecovery(ruleSet) else null,
        saturation = if (toneMappingEnabled) resolveEmbeddedMpvSaturation(ruleSet) else null,
        gamutMappingMode = if (toneMappingEnabled) resolveEmbeddedMpvGamutMappingMode(ruleSet, runtimeAbiIs32Bit) else null,
        deband = toneMappingEnabled && !runtimeAbiIs32Bit,
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

private fun resolveEmbeddedMpvGamutMappingMode(
    ruleSet: ToneMappingRuleSet,
    runtimeAbiIs32Bit: Boolean,
): String? =
    when {
        !ruleSet.enabled -> null
        ruleSet.gamutMappingMode != null -> ruleSet.gamutMappingMode
        runtimeAbiIs32Bit -> "clip"
        else -> "perceptual"
    }

private fun effectiveEmbeddedMpvPeakDetectionStrategy(
    strategy: PeakDetectionStrategy,
    runtimeAbiIs32Bit: Boolean,
): PeakDetectionStrategy =
    when {
        !runtimeAbiIs32Bit -> strategy
        strategy == PeakDetectionStrategy.DYNAMIC -> PeakDetectionStrategy.STATIC_METADATA
        strategy == PeakDetectionStrategy.DYNAMIC_AGGRESSIVE -> PeakDetectionStrategy.DYNAMIC
        else -> strategy
    }

private fun isEmbeddedMpvRuntime32Bit(): Boolean =
    runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> !Process.is64Bit()
            else -> Build.SUPPORTED_64_BIT_ABIS.isEmpty()
        }
    }.getOrDefault(false)

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

fun effectiveEmbeddedMpvVideoOutput(
    runtimeAbiIs32Bit: Boolean = isEmbeddedMpvRuntime32Bit(),
    debugConfig: EmbeddedMpvDebugConfig = EmbeddedMpvDebugConfig(),
): String = debugConfig.vo ?: if (runtimeAbiIs32Bit) "gpu-hq" else "gpu-next"

fun effectiveEmbeddedMpvHwdec(
    debugConfig: EmbeddedMpvDebugConfig = EmbeddedMpvDebugConfig(),
): String = debugConfig.hwdec ?: "mediacodec-copy"

private data class EmbeddedMpvPeakDetection(
    val enabled: Boolean,
    val peakPercentile: Float? = null,
    val peakDecayRate: Float? = null,
    val sceneThresholdLow: Float? = null,
    val sceneThresholdHigh: Float? = null,
)
