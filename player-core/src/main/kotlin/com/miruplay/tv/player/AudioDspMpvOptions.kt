package com.miruplay.tv.player

import com.miruplay.tv.audio.AudioDspPlanCompiler
import com.miruplay.tv.audio.ChannelLayout
import com.miruplay.tv.audio.FrequencyResponse
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspOutputMode
import com.miruplay.tv.model.AudioDspPhaseMode
import java.util.Locale
import kotlin.math.pow

private const val DSP_SAMPLE_RATE_HZ = 48_000

/**
 * Builds the common FFmpeg filter options consumed by embedded mpv and IJK.
 *
 * Native FFmpeg EQ filters calculate coefficients after the input sample rate
 * is known, so 44.1/48/96 kHz streams retain the configured center frequencies.
 * The linear-phase path is represented by firequalizer's sampled magnitude
 * curve and is intentionally generated from the same core compiler as Exo.
 */
fun buildAudioDspMpvOptions(config: AudioDspConfig): Map<String, String> {
    val filterChain = buildAudioDspFilterChain(config) ?: return emptyMap()
    return mapOf(
        "ao" to "audiotrack",
        "audio-spdif" to "no",
        "audio-exclusive" to "no",
        "audio-format" to "float",
        "audio-channels" to "auto",
        "af" to "lavfi=[$filterChain]",
    )
}

/**
 * Returns whether the embedded FFmpeg graph can preserve this preset's semantics.
 *
 * FFmpeg's firequalizer exposes one FIR response for all channels. Channel-specific
 * linear phase rules and runtime-dependent downmix/HRTF graphs therefore stay on
 * the standard Exo path, where the core processor has the complete channel layout.
 */
fun isAudioDspMpvCompatible(config: AudioDspConfig): Boolean {
    if (!config.enabled) return true
    val preset = config.presets.firstOrNull { it.id == config.selectedPresetId }
        ?: config.presets.firstOrNull()
        ?: return false
    val normalized = preset.normalized()
    if (normalized.outputMode != AudioDspOutputMode.AUTO_PRESERVE) return false
    if (normalized.phaseMode != AudioDspPhaseMode.LINEAR) return true
    return normalized.rules.all { it.target == AudioDspChannelTarget.ALL }
}

fun buildAudioDspIjkOptions(config: AudioDspConfig): Map<String, String> {
    val filterChain = buildAudioDspFilterChain(config) ?: return emptyMap()
    return mapOf(
        "af" to filterChain,
        "audio-format" to "f32",
        "audio-spdif" to "0",
    )
}

private fun buildAudioDspFilterChain(config: AudioDspConfig): String? {
    if (!config.enabled) return null
    if (!isAudioDspMpvCompatible(config)) return null
    val preset = config.presets.firstOrNull { it.id == config.selectedPresetId }
        ?: config.presets.firstOrNull()
        ?: return null
    val normalized = preset.normalized()
    val filters = mutableListOf<String>()
    if (normalized.preampDb != 0f) filters += "volume=${number(normalized.preampDb)}dB"

    if (normalized.phaseMode == AudioDspPhaseMode.LINEAR) {
        filters += buildLinearPhaseFilter(normalized)
    } else {
        normalized.rules.forEach { rule ->
            val selector = channelSelector(rule.target)
            rule.bands.filter { it.enabled }.forEach { band ->
                filters += band.toFfmpegFilter(selector)
            }
            if (rule.outputGainDb != 0f) {
                val gain = 10.0.pow(rule.outputGainDb.toDouble() / 20.0)
                filters += "biquad=b0=${number(gain)}:b1=0:b2=0:a1=0:a2=0:c=$selector"
            }
        }
    }
    if (normalized.phaseMode == AudioDspPhaseMode.LINEAR) {
        normalized.rules
            .filter { it.outputGainDb != 0f }
            .forEach { rule ->
                val gain = 10.0.pow(rule.outputGainDb.toDouble() / 20.0)
                filters += "biquad=b0=${number(gain)}:b1=0:b2=0:a1=0:a2=0:c=${channelSelector(rule.target)}"
            }
    }
    if (normalized.limiter.enabled) {
        val ceiling = 10.0.pow(normalized.limiter.ceilingDb.toDouble() / 20.0)
        filters += "alimiter=limit=${number(ceiling)}:release=${number(normalized.limiter.releaseMs)}"
    }
    // Non-preserving output modes are deliberately rejected by
    // isAudioDspMpvCompatible and routed through the core Exo processor.
    return filters.joinToString(",").ifBlank { "anull" }
}

private fun buildLinearPhaseFilter(preset: com.miruplay.tv.model.AudioDspPreset): String {
    val plan = AudioDspPlanCompiler.compile(preset, ChannelLayout.from(2, null), DSP_SAMPLE_RATE_HZ)
    val frequencies = FloatArray(96) { index ->
        val normalized = index.toFloat() / (95f)
        val nyquist = DSP_SAMPLE_RATE_HZ.toDouble() / 2.0
        (10.0 * (nyquist / 10.0).pow(normalized.toDouble())).toFloat()
    }
    val response = FrequencyResponse.sample(plan, frequencies)
    val entries = response.frequenciesHz.indices.joinToString(";") { index ->
        "${number(response.frequenciesHz[index])}:${number(response.magnitudeDb[index])}"
    }
    val delaySeconds = plan.groupDelayFrames.toDouble() / DSP_SAMPLE_RATE_HZ
    return "firequalizer=gain_entry='$entries':delay=${number(delaySeconds)}:multi=true:zero_phase=false"
}

private fun com.miruplay.tv.model.AudioDspBand.toFfmpegFilter(selector: String): String = when (type) {
    AudioDspFilterType.PEAKING ->
        "equalizer=f=${number(frequencyHz)}:t=q:w=${number(q)}:g=${number(gainDb)}:c=$selector"
    AudioDspFilterType.LOW_SHELF ->
        "bass=f=${number(frequencyHz)}:t=q:w=${number(q)}:g=${number(gainDb)}:c=$selector"
    AudioDspFilterType.HIGH_SHELF ->
        "treble=f=${number(frequencyHz)}:t=q:w=${number(q)}:g=${number(gainDb)}:c=$selector"
    AudioDspFilterType.LOW_PASS ->
        "lowpass=f=${number(frequencyHz)}:t=q:w=${number(q)}:c=$selector"
    AudioDspFilterType.HIGH_PASS ->
        "highpass=f=${number(frequencyHz)}:t=q:w=${number(q)}:c=$selector"
    AudioDspFilterType.NOTCH ->
        "bandreject=f=${number(frequencyHz)}:t=q:w=${number(q)}:c=$selector"
    AudioDspFilterType.BAND_PASS ->
        "bandpass=f=${number(frequencyHz)}:t=q:w=${number(q)}:c=$selector"
}

private fun channelSelector(target: AudioDspChannelTarget): String = when (target) {
    AudioDspChannelTarget.ALL -> "all"
    AudioDspChannelTarget.FRONT -> "FL+FR+FC"
    AudioDspChannelTarget.CENTER_LFE -> "FC+LFE"
    AudioDspChannelTarget.SURROUND -> "BL+BR+SL+SR"
    // FFmpeg's canonical 5.1 layout is FL+FR+FC+LFE+BL+BR. The core
    // layout uses LS/RS labels for the fifth and sixth positions, but mpv
    // receives the FFmpeg names and must select BL/BR here.
    AudioDspChannelTarget.SURROUND_5_1 -> "BL+BR"
    AudioDspChannelTarget.SURROUND_7_1 -> "BL+BR+SL+SR"
    AudioDspChannelTarget.LEFT -> "FL"
    AudioDspChannelTarget.RIGHT -> "FR"
    AudioDspChannelTarget.CENTER -> "FC"
    AudioDspChannelTarget.LFE -> "LFE"
    AudioDspChannelTarget.LEFT_SURROUND -> "BL+SL"
    AudioDspChannelTarget.RIGHT_SURROUND -> "BR+SR"
}

private fun number(value: Number): String {
    val double = value.toDouble()
    return if (double.isFinite() && double == double.toInt().toDouble()) {
        String.format(Locale.US, "%.1f", double)
    } else {
        String.format(Locale.US, "%.9g", double)
    }
}
