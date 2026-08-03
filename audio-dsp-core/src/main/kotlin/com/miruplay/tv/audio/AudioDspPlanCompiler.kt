package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspPhaseMode
import com.miruplay.tv.model.AudioDspPreset

data class CompiledDspPlan(
    val sampleRateHz: Int,
    val layout: ChannelLayout,
    val phaseMode: AudioDspPhaseMode,
    val outputMode: com.miruplay.tv.model.AudioDspOutputMode,
    val biquadsByChannel: List<List<BiquadCoefficients>>,
    val firTapsByChannel: List<FloatArray>,
    val groupDelayFrames: Int,
)

object AudioDspPlanCompiler {
    private const val RESPONSE_BINS = 512

    fun compile(preset: AudioDspPreset, layout: ChannelLayout, sampleRateHz: Int): CompiledDspPlan {
        require(sampleRateHz > 0) { "sample rate must be positive" }
        val normalized = preset.normalized()
        val chains = layout.channels.map { channel ->
            val rule = normalized.rules.firstOrNull { it.target.matches(channel, layout) }
                ?: AudioDspChannelRule()
            rule.bands.filter { it.enabled }.map { BiquadDesigner.design(it, sampleRateHz) }
        }
        val fir = if (normalized.phaseMode == AudioDspPhaseMode.LINEAR) {
            val frequencyGrid = FloatArray(RESPONSE_BINS) { index ->
                index.toFloat() / (RESPONSE_BINS - 1) * sampleRateHz / 2f
            }
            chains.map { chain ->
                val targetDb = frequencyGrid.map { frequency ->
                    var gain = 1.0
                    chain.forEach { gain *= it.magnitudeAt(frequency.toDouble().coerceAtLeast(1.0), sampleRateHz.toDouble()) }
                    (20.0 * kotlin.math.log10(gain.coerceAtLeast(1e-12))).toFloat()
                }.toFloatArray()
                LinearPhaseFirDesigner.design(targetDb, sampleRateHz, normalized.firQuality.taps)
            }
        } else {
            List(layout.channelCount) { FloatArray(0) }
        }
        return CompiledDspPlan(
            sampleRateHz = sampleRateHz,
            layout = layout,
            phaseMode = normalized.phaseMode,
            outputMode = normalized.outputMode,
            biquadsByChannel = chains,
            firTapsByChannel = fir,
            groupDelayFrames = if (normalized.phaseMode == AudioDspPhaseMode.LINEAR) (normalized.firQuality.taps - 1) / 2 else 0,
        )
    }

    private fun AudioDspChannelTarget.matches(channel: Channel, layout: ChannelLayout): Boolean = when (this) {
        AudioDspChannelTarget.ALL -> true
        AudioDspChannelTarget.FRONT -> channel == Channel.L || channel == Channel.R || channel == Channel.C
        AudioDspChannelTarget.CENTER_LFE -> channel == Channel.C || channel == Channel.LFE
        AudioDspChannelTarget.SURROUND, AudioDspChannelTarget.SURROUND_5_1, AudioDspChannelTarget.SURROUND_7_1 ->
            channel == Channel.LS || channel == Channel.RS || channel == Channel.LB || channel == Channel.RB
        AudioDspChannelTarget.LEFT -> channel == Channel.L
        AudioDspChannelTarget.RIGHT -> channel == Channel.R
        AudioDspChannelTarget.CENTER -> channel == Channel.C
        AudioDspChannelTarget.LFE -> channel == Channel.LFE
        AudioDspChannelTarget.LEFT_SURROUND -> channel == Channel.LS || channel == Channel.LB
        AudioDspChannelTarget.RIGHT_SURROUND -> channel == Channel.RS || channel == Channel.RB
    }
}
