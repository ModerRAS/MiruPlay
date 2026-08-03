@file:Suppress("UnsafeOptInUsageError", "RestrictedApi")

package com.miruplay.tv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@UnstableApi
class DspRenderersFactory(
    context: Context,
    private val runtimeConfig: AudioDspRuntimeConfig,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        val policy = AudioDspOutputPolicy.forConfig(runtimeConfig.config)
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput || policy.forcePcm)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(DspAudioProcessor(runtimeConfig)))
            .build()
    }
}
