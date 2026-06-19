package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlaybackRenderBackend {
    STANDARD_EXO,
    EXPERIMENTAL_GL,
    EXPERIMENTAL_MPV_ANDROID,
    EXPERIMENTAL_LIBVLC,
}

fun PlaybackRenderBackend.normalizeSupportedBackend(): PlaybackRenderBackend =
    when (this) {
        PlaybackRenderBackend.STANDARD_EXO -> PlaybackRenderBackend.STANDARD_EXO
        PlaybackRenderBackend.EXPERIMENTAL_GL -> PlaybackRenderBackend.EXPERIMENTAL_GL
        PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID -> PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID
        PlaybackRenderBackend.EXPERIMENTAL_LIBVLC -> PlaybackRenderBackend.STANDARD_EXO
    }

fun supportedPlaybackRenderBackends(): List<PlaybackRenderBackend> =
    listOf(
        PlaybackRenderBackend.STANDARD_EXO,
        PlaybackRenderBackend.EXPERIMENTAL_GL,
        PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID,
    )

@Serializable
enum class VideoSignalKind {
    SDR,
    HDR10,
    HDR10_PLUS,
    DOLBY_VISION,
    UNKNOWN_HDR,
}

@Serializable
enum class VideoTransferCharacteristic {
    SDR,
    PQ,
    HLG,
    SRGB,
    GAMMA_22,
    UNKNOWN,
}

@Serializable
enum class VideoColorPrimaries {
    BT709,
    BT2020,
    DISPLAY_P3,
    UNKNOWN,
}

@Serializable
enum class DolbyVisionProfile(
    val displayLabel: String,
) {
    PROFILE_4("P4"),
    PROFILE_5("P5"),
    PROFILE_7("P7"),
    PROFILE_8("P8"),
    PROFILE_8_1("P8.1"),
    PROFILE_8_4("P8.4"),
    PROFILE_9("P9"),
    UNKNOWN("DV"),
    ;

    companion object {
        private val codecRegex = Regex("""(?i)\b(?:dvhe|dvh1)\.(\d{2})\.(\d{2})""")

        fun fromCodecString(codec: String?): DolbyVisionProfile? {
            val match = codecRegex.find(codec.orEmpty()) ?: return null
            val profileCode = match.groupValues[1]
            val levelCode = match.groupValues[2]
            return when (profileCode) {
                "04" -> PROFILE_4
                "05" -> PROFILE_5
                "07" -> PROFILE_7
                "08" -> when (levelCode) {
                    "04" -> PROFILE_8_1
                    "07" -> PROFILE_8_4
                    else -> PROFILE_8
                }
                "09" -> PROFILE_9
                else -> UNKNOWN
            }
        }

        fun levelFromCodecString(codec: String?): String? =
            codecRegex.find(codec.orEmpty())?.groupValues?.getOrNull(2)
    }
}

@Serializable
enum class VideoRenderRuleKey {
    SDR,
    HDR10,
    HDR10_PLUS,
    DOLBY_VISION,
    UNKNOWN_HDR,
}

@Serializable
enum class ToneMappingCurvePreset {
    PASSTHROUGH,
    MOBIUS,
    REINHARD,
}

@Serializable
enum class PeakDetectionStrategy {
    DISABLED,
    STATIC_METADATA,
    DYNAMIC,
    DYNAMIC_AGGRESSIVE,
}

@Serializable
enum class FallbackBackendPolicy {
    KEEP_CURRENT,
    FALLBACK_TO_STANDARD,
}

@Serializable
data class ToneMappingRuleSet(
    val ruleKey: VideoRenderRuleKey,
    val enabled: Boolean,
    val curvePreset: ToneMappingCurvePreset,
    val targetSdrNits: Int,
    val peakDetectionStrategy: PeakDetectionStrategy,
    val saturationRecovery: Int,
    val contrastRecovery: Int,
    val highlightCompression: Int,
    val fallbackBackendPolicy: FallbackBackendPolicy,
) {
    fun summaryLabel(): String =
        when {
            !enabled -> "直通"
            else -> buildList {
                add(
                    when (curvePreset) {
                        ToneMappingCurvePreset.PASSTHROUGH -> "直通"
                        ToneMappingCurvePreset.MOBIUS -> "Mobius"
                        ToneMappingCurvePreset.REINHARD -> "Reinhard"
                    }
                )
                add("${targetSdrNits.coerceAtLeast(0)} nit")
                add(
                    when (peakDetectionStrategy) {
                        PeakDetectionStrategy.DISABLED -> "关闭峰值"
                        PeakDetectionStrategy.STATIC_METADATA -> "静态峰值"
                        PeakDetectionStrategy.DYNAMIC -> "动态峰值"
                        PeakDetectionStrategy.DYNAMIC_AGGRESSIVE -> "增强峰值"
                    }
                )
            }.joinToString(" · ")
        }
}

@Serializable
data class VideoSignalDescriptor(
    val signalKind: VideoSignalKind = VideoSignalKind.SDR,
    val transfer: VideoTransferCharacteristic = VideoTransferCharacteristic.SDR,
    val colorPrimaries: VideoColorPrimaries = VideoColorPrimaries.BT709,
    val bitDepth: Int = 8,
    val codecId: String = "",
    val dolbyVisionProfile: DolbyVisionProfile? = null,
    val dolbyVisionLevel: String? = null,
    val hasHdrStaticMetadata: Boolean = false,
    val hasHdr10PlusMetadata: Boolean = false,
) {
    fun toRenderRuleKey(): VideoRenderRuleKey =
        when (signalKind) {
            VideoSignalKind.SDR -> VideoRenderRuleKey.SDR
            VideoSignalKind.HDR10 -> VideoRenderRuleKey.HDR10
            VideoSignalKind.HDR10_PLUS -> VideoRenderRuleKey.HDR10_PLUS
            VideoSignalKind.DOLBY_VISION -> VideoRenderRuleKey.DOLBY_VISION
            VideoSignalKind.UNKNOWN_HDR -> VideoRenderRuleKey.UNKNOWN_HDR
        }

    fun displayLabel(): String =
        when (signalKind) {
            VideoSignalKind.SDR -> "SDR"
            VideoSignalKind.HDR10 -> "HDR10"
            VideoSignalKind.HDR10_PLUS -> "HDR10+"
            VideoSignalKind.DOLBY_VISION -> {
                val profileLabel = dolbyVisionProfile?.displayLabel
                if (profileLabel.isNullOrBlank() || profileLabel == DolbyVisionProfile.UNKNOWN.displayLabel) {
                    "Dolby Vision"
                } else {
                    "Dolby Vision $profileLabel"
                }
            }
            VideoSignalKind.UNKNOWN_HDR -> "Unknown HDR"
        }

    val isHdr: Boolean
        get() = signalKind != VideoSignalKind.SDR
}

@Serializable
data class FormatAwareToneMappingPreferences(
    val defaultBackend: PlaybackRenderBackend = PlaybackRenderBackend.STANDARD_EXO,
    val rules: Map<VideoRenderRuleKey, ToneMappingRuleSet> = defaultToneMappingRuleTable(),
) {
    fun normalized(): FormatAwareToneMappingPreferences {
        val mergedRules = defaultToneMappingRuleTable() + rules
        return copy(
            defaultBackend = defaultBackend.normalizeSupportedBackend(),
            rules = VideoRenderRuleKey.entries.associateWith { key ->
                val candidate = mergedRules[key] ?: defaultToneMappingRuleSet(key)
                if (candidate.ruleKey == key) candidate else candidate.copy(ruleKey = key)
            }
        )
    }
}

fun defaultToneMappingRuleTable(): Map<VideoRenderRuleKey, ToneMappingRuleSet> =
    VideoRenderRuleKey.entries.associateWith(::defaultToneMappingRuleSet)

fun defaultToneMappingRuleSet(
    ruleKey: VideoRenderRuleKey,
): ToneMappingRuleSet =
    when (ruleKey) {
        VideoRenderRuleKey.SDR -> ToneMappingRuleSet(
            ruleKey = ruleKey,
            enabled = false,
            curvePreset = ToneMappingCurvePreset.PASSTHROUGH,
            targetSdrNits = 100,
            peakDetectionStrategy = PeakDetectionStrategy.DISABLED,
            saturationRecovery = 0,
            contrastRecovery = 0,
            highlightCompression = 0,
            fallbackBackendPolicy = FallbackBackendPolicy.KEEP_CURRENT,
        )
        VideoRenderRuleKey.HDR10 -> ToneMappingRuleSet(
            ruleKey = ruleKey,
            enabled = true,
            curvePreset = ToneMappingCurvePreset.MOBIUS,
            targetSdrNits = 120,
            peakDetectionStrategy = PeakDetectionStrategy.DYNAMIC,
            saturationRecovery = 10,
            contrastRecovery = 8,
            highlightCompression = 18,
            fallbackBackendPolicy = FallbackBackendPolicy.KEEP_CURRENT,
        )
        VideoRenderRuleKey.HDR10_PLUS -> ToneMappingRuleSet(
            ruleKey = ruleKey,
            enabled = true,
            curvePreset = ToneMappingCurvePreset.MOBIUS,
            targetSdrNits = 120,
            peakDetectionStrategy = PeakDetectionStrategy.DYNAMIC_AGGRESSIVE,
            saturationRecovery = 12,
            contrastRecovery = 10,
            highlightCompression = 24,
            fallbackBackendPolicy = FallbackBackendPolicy.KEEP_CURRENT,
        )
        VideoRenderRuleKey.DOLBY_VISION -> ToneMappingRuleSet(
            ruleKey = ruleKey,
            enabled = true,
            curvePreset = ToneMappingCurvePreset.MOBIUS,
            targetSdrNits = 120,
            peakDetectionStrategy = PeakDetectionStrategy.DYNAMIC,
            saturationRecovery = 8,
            contrastRecovery = 6,
            highlightCompression = 18,
            fallbackBackendPolicy = FallbackBackendPolicy.FALLBACK_TO_STANDARD,
        )
        VideoRenderRuleKey.UNKNOWN_HDR -> ToneMappingRuleSet(
            ruleKey = ruleKey,
            enabled = true,
            curvePreset = ToneMappingCurvePreset.MOBIUS,
            targetSdrNits = 120,
            peakDetectionStrategy = PeakDetectionStrategy.STATIC_METADATA,
            saturationRecovery = 8,
            contrastRecovery = 6,
            highlightCompression = 16,
            fallbackBackendPolicy = FallbackBackendPolicy.FALLBACK_TO_STANDARD,
        )
    }
