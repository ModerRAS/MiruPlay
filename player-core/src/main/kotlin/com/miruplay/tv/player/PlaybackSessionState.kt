package com.miruplay.tv.player

import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey

internal data class PlaybackSessionState(
    val requestedBackendOverride: PlaybackRenderBackend? = null,
    val ruleOverrides: Map<VideoRenderRuleKey, ToneMappingRuleSet> = emptyMap(),
) {
    fun effectiveRequestedBackend(defaultBackend: PlaybackRenderBackend): PlaybackRenderBackend =
        requestedBackendOverride ?: defaultBackend

    fun withRequestedBackendOverride(backend: PlaybackRenderBackend?): PlaybackSessionState =
        copy(requestedBackendOverride = backend)

    fun withRuleOverride(
        ruleKey: VideoRenderRuleKey,
        ruleSet: ToneMappingRuleSet?,
    ): PlaybackSessionState =
        copy(
            ruleOverrides = ruleOverrides.toMutableMap().apply {
                if (ruleSet == null) remove(ruleKey) else put(ruleKey, ruleSet)
            }.toMap()
        )

    fun clearRuleOverrides(): PlaybackSessionState =
        copy(ruleOverrides = emptyMap())

    fun afterPlaybackReset(clearSessionState: Boolean): PlaybackSessionState =
        if (clearSessionState) PlaybackSessionState() else this
}
