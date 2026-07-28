package com.miruplay.tv.player

import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.normalizeSupportedBackend
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
    val requestedBackend =
        (requestedBackendOverride ?: normalizedPreferences.defaultBackend).normalizeSupportedBackend()
    val fallbackReason = backendStatusMessage(
        requestedBackend = requestedBackend,
        ruleKey = ruleKey,
        signalDescriptor = signalDescriptor,
    )
    val activeBackend = when {
        requestedBackend == PlaybackRenderBackend.EXPERIMENTAL_IJKPLAYER && signalDescriptor.isHdr ->
            PlaybackRenderBackend.STANDARD_EXO
        else -> requestedBackend
    }
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
    if (requestedBackend == PlaybackRenderBackend.EXPERIMENTAL_IJKPLAYER && signalDescriptor.isHdr) {
        return "ijkplayer 尚未验证 HDR，已回退到标准 Exo"
    }
    return null
}
