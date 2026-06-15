package com.miruplay.tv.player

import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalDescriptor

data class ToneMappingRuntimeConfig(
    val ruleKey: VideoRenderRuleKey,
    val appliedRuleSet: ToneMappingRuleSet,
    val requestedBackend: PlaybackRenderBackend,
    val activeBackend: PlaybackRenderBackend,
    val fallbackReason: String? = null,
)

fun resolveToneMappingRuntimeConfig(
    preferences: FormatAwareToneMappingPreferences,
    sessionRuleOverrides: Map<VideoRenderRuleKey, ToneMappingRuleSet>,
    signalDescriptor: VideoSignalDescriptor,
    requestedBackendOverride: PlaybackRenderBackend?,
): ToneMappingRuntimeConfig {
    val normalizedPreferences = preferences.normalized()
    val ruleKey = signalDescriptor.toRenderRuleKey()
    val appliedRuleSet = sessionRuleOverrides[ruleKey]
        ?: normalizedPreferences.rules.getValue(ruleKey)
    val requestedBackend = requestedBackendOverride ?: normalizedPreferences.defaultBackend
    val fallbackReason = backendStatusMessage(
        requestedBackend = requestedBackend,
        ruleKey = ruleKey,
        signalDescriptor = signalDescriptor,
    )
    val activeBackend = requestedBackend
    return ToneMappingRuntimeConfig(
        ruleKey = ruleKey,
        appliedRuleSet = appliedRuleSet,
        requestedBackend = requestedBackend,
        activeBackend = activeBackend,
        fallbackReason = fallbackReason,
    )
}

private fun backendStatusMessage(
    requestedBackend: PlaybackRenderBackend,
    ruleKey: VideoRenderRuleKey,
    signalDescriptor: VideoSignalDescriptor,
): String? {
    if (requestedBackend == PlaybackRenderBackend.STANDARD_EXO) {
        return null
    }
    return when {
        requestedBackend == PlaybackRenderBackend.EXPERIMENTAL_LIBVLC &&
            (ruleKey == VideoRenderRuleKey.DOLBY_VISION ||
                signalDescriptor.signalKind == com.miruplay.tv.model.VideoSignalKind.DOLBY_VISION) -> {
            "Dolby Vision 已走 VLC 新后端；当前版本优先保证可播，动态映射细化仍在完善。"
        }
        else -> null
    }
}
