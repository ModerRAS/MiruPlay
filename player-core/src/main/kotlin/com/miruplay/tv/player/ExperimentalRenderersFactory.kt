@file:android.annotation.SuppressLint("RestrictedApi")

package com.miruplay.tv.player

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@UnstableApi
class ExperimentalRenderersFactory(
    context: Context,
    private val audioDspRuntimeConfig: AudioDspRuntimeConfig,
) : DefaultRenderersFactory(context) {
    private val experimentalVideoPipelineMode =
        resolveExperimentalVideoPipelineMode(resolveDeviceGlEsMajorVersion(context))

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(DspAudioProcessor(audioDspRuntimeConfig)))
        .build()

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

        if (experimentalVideoPipelineMode == ExperimentalVideoPipelineMode.MEDIA3_EFFECTS) {
            Log.i(
                "ExperimentalRenderersFactory",
                "Using Media3 native effects renderer",
            )
            return
        }

        val replacementRenderer = ExperimentalHdrSurfaceMediaCodecVideoRenderer(
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
        out[rendererIndex] = replacementRenderer
        Log.i(
            "ExperimentalRenderersFactory",
            "Configured experimental renderer mode=$experimentalVideoPipelineMode renderer=${replacementRenderer.javaClass.simpleName}",
        )
    }
}
