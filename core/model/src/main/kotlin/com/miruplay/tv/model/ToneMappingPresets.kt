package com.miruplay.tv.model

import kotlin.math.max
import kotlinx.serialization.Serializable

@Serializable
enum class ToneMappingProfilePreset {
    BYPASS,
    BALANCED,
    PUNCHY,
    SOFT,
}

fun toneMappingPresetLabel(preset: ToneMappingProfilePreset): String =
    when (preset) {
        ToneMappingProfilePreset.BYPASS -> "直通"
        ToneMappingProfilePreset.BALANCED -> "平衡"
        ToneMappingProfilePreset.PUNCHY -> "偏浓"
        ToneMappingProfilePreset.SOFT -> "柔和"
    }

fun toneMappingPresetOptions(): List<ToneMappingProfilePreset> =
    ToneMappingProfilePreset.entries

fun buildToneMappingPreset(
    ruleKey: VideoRenderRuleKey,
    preset: ToneMappingProfilePreset,
): ToneMappingRuleSet {
    val base = defaultToneMappingRuleSet(ruleKey)
    return when (preset) {
        ToneMappingProfilePreset.BYPASS -> base.copy(
            enabled = false,
            curvePreset = ToneMappingCurvePreset.PASSTHROUGH,
            peakDetectionStrategy = PeakDetectionStrategy.DISABLED,
            saturationRecovery = 0,
            contrastRecovery = 0,
            highlightCompression = 0,
        )
        ToneMappingProfilePreset.BALANCED -> base
        ToneMappingProfilePreset.PUNCHY -> base.copy(
            targetSdrNits = base.targetSdrNits + if (ruleKey == VideoRenderRuleKey.SDR) 0 else 20,
            saturationRecovery = base.saturationRecovery + if (ruleKey == VideoRenderRuleKey.SDR) 6 else 8,
            contrastRecovery = base.contrastRecovery + if (ruleKey == VideoRenderRuleKey.SDR) 4 else 6,
            highlightCompression = max(0, base.highlightCompression - 4),
        )
        ToneMappingProfilePreset.SOFT -> base.copy(
            targetSdrNits = max(80, base.targetSdrNits - 15),
            saturationRecovery = max(0, base.saturationRecovery - 4),
            contrastRecovery = max(0, base.contrastRecovery - 4),
            highlightCompression = base.highlightCompression + 6,
        )
    }
}

fun ToneMappingRuleSet.toApproximatePreset(): ToneMappingProfilePreset =
    when {
        !enabled || curvePreset == ToneMappingCurvePreset.PASSTHROUGH -> ToneMappingProfilePreset.BYPASS
        targetSdrNits > defaultToneMappingRuleSet(ruleKey).targetSdrNits -> ToneMappingProfilePreset.PUNCHY
        highlightCompression > defaultToneMappingRuleSet(ruleKey).highlightCompression -> ToneMappingProfilePreset.SOFT
        else -> ToneMappingProfilePreset.BALANCED
    }

fun ToneMappingRuleSet.adjustForSession(
    targetSdrNitsDelta: Int = 0,
    saturationRecoveryDelta: Int = 0,
    contrastRecoveryDelta: Int = 0,
    highlightCompressionDelta: Int = 0,
): ToneMappingRuleSet {
    val shouldEnable =
        targetSdrNitsDelta != 0 ||
            saturationRecoveryDelta != 0 ||
            contrastRecoveryDelta != 0 ||
            highlightCompressionDelta != 0
    val base = if (enabled || !shouldEnable) {
        this
    } else {
        defaultToneMappingRuleSet(ruleKey).copy(
            enabled = true,
            curvePreset = ToneMappingCurvePreset.MOBIUS,
        )
    }
    return base.copy(
        enabled = base.enabled || shouldEnable,
        targetSdrNits = (base.targetSdrNits + targetSdrNitsDelta).coerceIn(80, 240),
        saturationRecovery = (base.saturationRecovery + saturationRecoveryDelta).coerceIn(0, 100),
        contrastRecovery = (base.contrastRecovery + contrastRecoveryDelta).coerceIn(0, 100),
        highlightCompression = (base.highlightCompression + highlightCompressionDelta).coerceIn(0, 100),
    )
}
