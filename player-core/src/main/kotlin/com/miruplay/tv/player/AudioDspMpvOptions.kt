package com.miruplay.tv.player

import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspOutputMode
import com.miruplay.tv.model.AudioDspPhaseMode
import kotlin.math.pow

fun buildAudioDspMpvOptions(config: AudioDspConfig): Map<String, String> {
    if (!config.enabled) return emptyMap()
    val preset = config.presets.firstOrNull { it.id == config.selectedPresetId }
        ?: config.presets.firstOrNull()
        ?: return emptyMap()
    val filters = mutableListOf<String>()
    if (preset.preampDb != 0f) filters += "volume=${preset.preampDb}dB"
    if (preset.phaseMode == AudioDspPhaseMode.LINEAR) {
        val entries = preset.rules.flatMap { it.bands }.filter { it.enabled }
            .joinToString(";") { band -> "${band.frequencyHz}:${band.gainDb}" }
        filters += "firequalizer=gain_entry='${entries.ifBlank { "0:0" }}'"
    } else {
        preset.rules.flatMap { it.bands }.filter { it.enabled }.forEach { band ->
            val kind = when (band.type) {
                AudioDspFilterType.PEAKING -> "biquad"
                AudioDspFilterType.LOW_SHELF -> "lowshelf"
                AudioDspFilterType.HIGH_SHELF -> "highshelf"
                AudioDspFilterType.LOW_PASS -> "lowpass"
                AudioDspFilterType.HIGH_PASS -> "highpass"
                AudioDspFilterType.NOTCH -> "bandreject"
                AudioDspFilterType.BAND_PASS -> "bandpass"
            }
            filters += "$kind=f=${band.frequencyHz}:g=${band.gainDb}:w=${band.q}"
        }
    }
    preset.rules
        .filter { it.target == com.miruplay.tv.model.AudioDspChannelTarget.ALL && it.outputGainDb != 0f }
        .forEach { rule -> filters += "volume=${rule.outputGainDb}dB" }
    if (preset.limiter.enabled) {
        val ceiling = 10.0.pow(preset.limiter.ceilingDb.toDouble() / 20.0)
        filters += "alimiter=limit=$ceiling:release=${preset.limiter.releaseMs}"
    }
    when (preset.outputMode) {
        AudioDspOutputMode.STEREO_DOWNMIX -> filters += "pan=stereo|c0=0.707*c0+0.707*c2+0.5*c4+0.5*c6|c1=0.707*c1+0.707*c2+0.5*c5+0.5*c7"
        AudioDspOutputMode.HRTF_BINAURAL -> filters += "headphone=map=FL|FR|FC|BL|BR"
        AudioDspOutputMode.AUTO_PRESERVE -> Unit
    }
    return mapOf(
        "ao" to "audiotrack",
        "audio-spdif" to "no",
        "audio-exclusive" to "no",
        "audio-format" to "float",
        "audio-channels" to "auto",
        "af" to filters.joinToString(",").ifBlank { "lavfi=[anull]" },
    )
}
