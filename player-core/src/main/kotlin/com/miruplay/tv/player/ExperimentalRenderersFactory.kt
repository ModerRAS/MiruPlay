package com.miruplay.tv.player

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.media3.common.PreviewingVideoGraph
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.CompositingVideoSinkProvider
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

@UnstableApi
class ExperimentalRenderersFactory(
    context: Context,
    private val previewingVideoGraphFactory: PreviewingVideoGraph.Factory,
) : DefaultRenderersFactory(context) {
    private val experimentalVideoPipelineMode =
        resolveExperimentalVideoPipelineMode(resolveDeviceGlEsMajorVersion(context))

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out,
        )

        val rendererIndex = out.indexOfFirst { it is MediaCodecVideoRenderer }
        if (rendererIndex < 0) {
            return
        }

        val replacementRenderer = when (experimentalVideoPipelineMode) {
            ExperimentalVideoPipelineMode.MEDIA3_EFFECTS -> MediaCodecVideoRenderer(
                context,
                getCodecAdapterFactory(),
                mediaCodecSelector,
                allowedVideoJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
                30f,
                CompositingVideoSinkProvider.Builder(context)
                    .setPreviewingVideoGraphFactory(previewingVideoGraphFactory)
                    .build(),
            )

            ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE -> ExperimentalHdrSurfaceMediaCodecVideoRenderer(
                context = context,
                mediaCodecAdapterFactory = getCodecAdapterFactory(),
                mediaCodecSelector = mediaCodecSelector,
                allowedVideoJoiningTimeMs = allowedVideoJoiningTimeMs,
                enableDecoderFallback = enableDecoderFallback,
                eventHandler = eventHandler,
                eventListener = eventListener,
                maxDroppedFramesToNotify = MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
                experimentalVideoPipelineMode = experimentalVideoPipelineMode,
            )
        }
        out[rendererIndex] = replacementRenderer
        Log.i(
            "ExperimentalRenderersFactory",
            "Configured experimental renderer mode=$experimentalVideoPipelineMode renderer=${replacementRenderer.javaClass.simpleName}",
        )
    }
}
