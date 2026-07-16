@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.content.Context
import androidx.media3.common.ColorInfo
import androidx.media3.common.DebugViewProvider
import androidx.media3.common.Effect
import androidx.media3.common.PreviewingVideoGraph
import androidx.media3.common.VideoGraph
import androidx.media3.effect.PreviewingSingleInputVideoGraph
import java.util.concurrent.Executor

internal fun resolveExperimentalGraphOutputColorInfo(
    inputColorInfo: ColorInfo,
    requestedOutputColorInfo: ColorInfo,
): ColorInfo =
    if (ColorInfo.isTransferHdr(inputColorInfo)) {
        ColorInfo.SDR_BT709_LIMITED
    } else {
        requestedOutputColorInfo
    }

internal fun shouldUseExoVideoEffectsPipeline(
    effectPipelineEnabled: Boolean,
    activeBackend: com.miruplay.tv.model.PlaybackRenderBackend,
    usesExperimentalEffectsPlayer: Boolean,
): Boolean =
    effectPipelineEnabled &&
        activeBackend == com.miruplay.tv.model.PlaybackRenderBackend.EXPERIMENTAL_GL &&
        usesExperimentalEffectsPlayer

internal class ExperimentalHdrSdrPreviewingVideoGraphFactory(
    private val delegate: PreviewingVideoGraph.Factory = PreviewingSingleInputVideoGraph.Factory(),
) : PreviewingVideoGraph.Factory {
    override fun create(
        context: Context,
        inputColorInfo: ColorInfo,
        outputColorInfo: ColorInfo,
        debugViewProvider: DebugViewProvider,
        listener: VideoGraph.Listener,
        listenerExecutor: Executor,
        compositionEffects: List<Effect>,
        initialTimestampOffsetUs: Long,
    ): PreviewingVideoGraph =
        delegate.create(
            context,
            inputColorInfo,
            resolveExperimentalGraphOutputColorInfo(
                inputColorInfo = inputColorInfo,
                requestedOutputColorInfo = outputColorInfo,
            ),
            debugViewProvider,
            listener,
            listenerExecutor,
            compositionEffects,
            initialTimestampOffsetUs,
        )
}
