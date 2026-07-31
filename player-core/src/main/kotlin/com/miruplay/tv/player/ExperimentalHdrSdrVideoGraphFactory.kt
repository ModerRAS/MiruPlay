@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

internal fun shouldUseExoVideoEffectsPipeline(
    effectPipelineEnabled: Boolean,
    activeBackend: com.miruplay.tv.model.PlaybackRenderBackend,
    usesExperimentalEffectsPlayer: Boolean,
): Boolean =
    effectPipelineEnabled &&
        activeBackend == com.miruplay.tv.model.PlaybackRenderBackend.EXPERIMENTAL_GL &&
        usesExperimentalEffectsPlayer
