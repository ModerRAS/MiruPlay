package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspPhaseMode
import com.miruplay.tv.model.AudioDspPreset
import com.miruplay.tv.model.AudioDspLimiter
import kotlin.math.pow

data class CompiledDspPlan(
    val sampleRateHz: Int,
    val layout: ChannelLayout,
    val phaseMode: AudioDspPhaseMode,
    val outputMode: com.miruplay.tv.model.AudioDspOutputMode,
    val biquadsByChannel: List<List<BiquadCoefficients>>,
    val firTapsByChannel: List<FloatArray>,
    val groupDelayFrames: Int,
    val preampLinear: Float = 1f,
    val channelGainLinear: FloatArray = FloatArray(layout.channelCount) { 1f },
    val limiter: AudioDspLimiter = AudioDspLimiter(),
) {
    val outputChannelCount: Int
        get() = if (outputMode == com.miruplay.tv.model.AudioDspOutputMode.AUTO_PRESERVE || layout.channelCount <= 2) {
            layout.channelCount
        } else {
            2
        }
}

object AudioDspPlanCompiler {
    private const val RESPONSE_BINS = 512

    fun compile(preset: AudioDspPreset, layout: ChannelLayout, sampleRateHz: Int): CompiledDspPlan {
        require(sampleRateHz > 0) { "sample rate must be positive" }
        val normalized = preset.normalized()
        val matchingRulesByChannel = layout.channels.map { channel ->
            normalized.rules.filter { it.target.matches(channel, layout) }
        }
        val chains = matchingRulesByChannel.map { rules ->
            rules.asSequence()
                .flatMap { it.bands.asSequence() }
                .filter { it.enabled }
                .map { BiquadDesigner.design(it, sampleRateHz) }
                .toList()
        }
        val channelGainLinear = matchingRulesByChannel.map { rules ->
            val gainDb = rules.sumOf { it.outputGainDb.toDouble() }
            10.0.pow(gainDb / 20.0).toFloat()
        }.toFloatArray()
        // auto headroom: if enabled, reduce preamp so peak after EQ never exceeds 0dB (clamped to -24..12)
        val effectivePreampDb = if (normalized.autoHeadroom) {
            val maxFilterGainDb = computeMaxGainDb(chains, normalized, sampleRateHz)
            val maxChannelGainDb = (channelGainLinear.maxOrNull()?.let { 20 * kotlin.math.log10(it.toDouble().coerceAtLeast(1e-12)) } ?: 0.0)
            val peakDb = normalized.preampDb + maxFilterGainDb + maxChannelGainDb
            if (peakDb > 0) (normalized.preampDb - peakDb.toFloat()).coerceIn(-24f, 12f) else normalized.preampDb
        } else normalized.preampDb
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
            biquadsByChannel = if (normalized.phaseMode == AudioDspPhaseMode.LINEAR) {
                List(layout.channelCount) { emptyList() }
            } else {
                chains
            },
            firTapsByChannel = fir,
            groupDelayFrames = if (normalized.phaseMode == AudioDspPhaseMode.LINEAR) (normalized.firQuality.taps - 1) / 2 else 0,
            preampLinear = 10.0.pow(effectivePreampDb.toDouble() / 20.0).toFloat(),
            channelGainLinear = channelGainLinear,
            limiter = normalized.limiter,
        )
    }

    private fun computeMaxGainDb(chains: List<List<BiquadCoefficients>>, preset: AudioDspPreset, sampleRateHz: Int): Double {
        if (chains.all { it.isEmpty() }) return 0.0
        var maxDb = Double.NEGATIVE_INFINITY
        // sample log-spaced frequencies 20..20k, 64 points
        val points = 64
        for (i in 0 until points) {
            val freq = 20.0 * Math.pow(20000.0 / 20.0, i.toDouble() / (points - 1))
            for (chain in chains) {
                var gain = 1.0
                chain.forEach { gain *= it.magnitudeAt(freq.coerceAtLeast(1.0), sampleRateHz.toDouble()) }
                val db = 20.0 * kotlin.math.log10(gain.coerceAtLeast(1e-12))
                if (db > maxDb) maxDb = db
            }
        }
        return maxDb.coerceAtLeast(0.0)
    }

    private fun AudioDspChannelTarget.matches(channel: Channel, layout: ChannelLayout): Boolean = when (this) {
        AudioDspChannelTarget.ALL -> true
        AudioDspChannelTarget.FRONT -> channel == Channel.L || channel == Channel.R || channel == Channel.C
        AudioDspChannelTarget.CENTER_LFE -> channel == Channel.C || channel == Channel.LFE
        AudioDspChannelTarget.SURROUND ->
            channel == Channel.LS || channel == Channel.RS || channel == Channel.LB || channel == Channel.RB
        AudioDspChannelTarget.SURROUND_5_1 ->
            layout.id == ChannelLayoutId.SURROUND_5_1 && (channel == Channel.LS || channel == Channel.RS)
        AudioDspChannelTarget.SURROUND_7_1 ->
            layout.id == ChannelLayoutId.SURROUND_7_1 &&
                (channel == Channel.LS || channel == Channel.RS || channel == Channel.LB || channel == Channel.RB)
        AudioDspChannelTarget.LEFT -> channel == Channel.L
        AudioDspChannelTarget.RIGHT -> channel == Channel.R
        AudioDspChannelTarget.CENTER -> channel == Channel.C
        AudioDspChannelTarget.LFE -> channel == Channel.LFE
        AudioDspChannelTarget.LEFT_SURROUND -> channel == Channel.LS || channel == Channel.LB
        AudioDspChannelTarget.RIGHT_SURROUND -> channel == Channel.RS || channel == Channel.RB
    }
}
