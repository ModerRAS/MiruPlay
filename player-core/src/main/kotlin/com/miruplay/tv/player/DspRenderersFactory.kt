@file:Suppress("UnsafeOptInUsageError", "RestrictedApi")

package com.miruplay.tv.player

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import com.miruplay.tv.model.MusicSrcBypassMode
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
        val mode = runtimeConfig.musicBypassMode
        val policy = if (mode == MusicSrcBypassMode.SYSTEM) {
            AudioDspOutputPolicy.forConfig(runtimeConfig.config)
        } else {
            AudioDspOutputPolicy.forMusic(runtimeConfig.config, mode)
        }
        val nativeRate = try {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        } catch (_: Exception) { 48000 }
        val processors = when (mode) {
            MusicSrcBypassMode.SOFTWARE -> arrayOf(
                DspAudioProcessor(runtimeConfig),
                MusicSrcBypassProcessor(nativeRate)
            )
            else -> arrayOf(DspAudioProcessor(runtimeConfig))
        }
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput || policy.forcePcm)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(processors)
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
