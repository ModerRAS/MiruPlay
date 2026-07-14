@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import androidx.media3.common.Effect
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbAdjustment
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import kotlin.math.abs

fun buildExoVideoEffects(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
): List<Effect> {
    val params = resolveExoVideoEffectParams(ruleSet, signalDescriptor) ?: return emptyList()
    val effects = mutableListOf<Effect>()
    if (abs(params.contrast) > 0.001f) {
        effects += Contrast(params.contrast)
    }
    effects += RgbAdjustment.Builder()
        .setRedScale(params.redScale)
        .setGreenScale(params.greenScale)
        .setBlueScale(params.blueScale)
        .build()
    return effects
}

data class ExoVideoEffectParams(
    val contrast: Float,
    val redScale: Float,
    val greenScale: Float,
    val blueScale: Float,
)

fun resolveExoVideoEffectParams(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
): ExoVideoEffectParams? {
    if (!ruleSet.enabled) {
        return null
    }
    val signalKind = signalDescriptor?.signalKind ?: VideoSignalKind.SDR
    if (signalKind == VideoSignalKind.SDR) {
        return null
    }

    val contrast = normalizedContrast(ruleSet)
    val rgbScale = resolveRgbScale(ruleSet, signalKind) ?: return if (abs(contrast) > 0.001f) {
        ExoVideoEffectParams(
            contrast = contrast,
            redScale = 1f,
            greenScale = 1f,
            blueScale = 1f,
        )
    } else {
        null
    }
    return ExoVideoEffectParams(
        contrast = contrast,
        redScale = rgbScale.redScale,
        greenScale = rgbScale.greenScale,
        blueScale = rgbScale.blueScale,
    )
}

private fun normalizedContrast(ruleSet: ToneMappingRuleSet): Float {
    val curveBias = when (ruleSet.curvePreset) {
        ToneMappingCurvePreset.PASSTHROUGH -> 0f
        ToneMappingCurvePreset.MOBIUS -> 0.04f
        ToneMappingCurvePreset.REINHARD -> 0.02f
    }
    return (ruleSet.contrastRecovery / 100f + curveBias).coerceIn(-0.9f, 0.9f)
}

private data class RgbScaleParams(
    val redScale: Float,
    val greenScale: Float,
    val blueScale: Float,
)

private fun resolveRgbScale(
    ruleSet: ToneMappingRuleSet,
    signalKind: VideoSignalKind,
): RgbScaleParams? {
    val peakCompression = (ruleSet.highlightCompression / 100f).coerceIn(0f, 0.6f)
    val saturationLift = (ruleSet.saturationRecovery / 100f).coerceIn(-0.5f, 0.8f)
    val hdrBoost = when (signalKind) {
        VideoSignalKind.HDR10_PLUS -> 0.12f
        VideoSignalKind.HDR10 -> 0.08f
        VideoSignalKind.DOLBY_VISION -> 0.06f
        VideoSignalKind.UNKNOWN_HDR -> 0.04f
        VideoSignalKind.SDR -> 0f
    }
    val curveBoost = when (ruleSet.curvePreset) {
        ToneMappingCurvePreset.PASSTHROUGH -> 0f
        ToneMappingCurvePreset.MOBIUS -> 0.04f
        ToneMappingCurvePreset.REINHARD -> 0.02f
    }
    val exposureLift = ((ruleSet.targetSdrNits - 100f) / 400f).coerceIn(-0.2f, 0.25f)
    val redScale = (1f + hdrBoost + exposureLift + curveBoost - peakCompression * 0.45f).coerceAtLeast(0f)
    val greenScale = (1f + hdrBoost * 0.9f + exposureLift - peakCompression * 0.4f).coerceAtLeast(0f)
    val blueScale = (1f + hdrBoost + exposureLift + saturationLift * 0.25f - peakCompression * 0.3f).coerceAtLeast(0f)
    if (
        abs(redScale - 1f) < 0.001f &&
        abs(greenScale - 1f) < 0.001f &&
        abs(blueScale - 1f) < 0.001f
    ) {
        return null
    }
    return RgbScaleParams(
        redScale = redScale,
        greenScale = greenScale,
        blueScale = blueScale,
    )
}
