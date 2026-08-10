@file:Suppress("UnsafeOptInUsageError", "RestrictedApi")

package com.miruplay.tv.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.text.TextOutput

@UnstableApi
class DspRenderersFactory(
    context: Context,
    private val runtimeConfig: AudioDspRuntimeConfig,
    private val libassSession: LibassSubtitleSession,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        val policy = AudioDspOutputPolicy.forConfig(runtimeConfig.config)
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput || policy.forcePcm)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(DspAudioProcessor(runtimeConfig)))
            .build()
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) = addLibassTextRenderers(libassSession, output, outputLooper, out)
}
