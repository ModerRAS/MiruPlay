package com.miruplay.tv.measure

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.AudioDspPreset

/**
 * Phase 4 injection: turn a measurement result into an [AudioDspPreset] the
 * existing DSP pipeline can apply. Auto-fit only produces PEAKING bands, which
 * map 1:1 onto [AudioDspBand]; the fitter's own constraints (Q caps, t60,
 * cut-only) are the source of truth, model bounds are the final clamp via
 * [AudioDspBand.normalized].
 */
object MeasuredPresetFactory {

    data class MeasuredBands(
        val bands: List<AudioDspBand>,
        /** False when the measurement was rejected by the fail-closed gate. */
        val valid: Boolean,
        val invalidReason: String? = null,
    )

    const val PRESET_ID_PREFIX = "measured-"

    /**
     * Build a new preset holding the measured rule for [target]. [timestampMs]
     * keeps this pure (callers decide the clock); pass the same value for
     * repeat calls to overwrite the previous measured preset.
     */
    fun buildPreset(
        measured: MeasuredBands,
        timestampMs: Long,
        target: AudioDspChannelTarget = AudioDspChannelTarget.ALL,
        presetName: String? = null,
    ): AudioDspPreset {
        require(measured.valid) { measured.invalidReason ?: "measurement is invalid" }
        val id = "$PRESET_ID_PREFIX$timestampMs"
        val rule = AudioDspChannelRule(
            target = target,
            bands = measured.bands.take(AudioDspChannelRule.MAX_BANDS_PER_RULE),
            outputGainDb = 0f, // cut-only fit: no headroom correction needed
        )
        return AudioDspPreset(
            id = id,
            name = presetName ?: "房间校准 ${formatTimestamp(timestampMs)}",
            rules = listOf(rule),
        ).normalized()
    }

    /**
     * Add the measured preset to [config] and select it. Existing presets and
     * the enabled switch are preserved; a previous measured preset with the
     * same timestamp id is replaced.
     */
    fun applyToConfig(
        config: AudioDspConfig,
        measured: MeasuredBands,
        timestampMs: Long,
        target: AudioDspChannelTarget = AudioDspChannelTarget.ALL,
        presetName: String? = null,
    ): AudioDspConfig {
        require(measured.valid) { measured.invalidReason ?: "measurement is invalid" }
        val preset = buildPreset(measured, timestampMs, target, presetName)
        val updated = config.normalized().copy(
            presets = config.normalized().presets.filterNot { it.id == preset.id } + preset,
            selectedPresetId = preset.id,
        )
        return updated.normalized()
    }

    /** Local wall-clock time, not UTC (Instant.toString() is UTC). */
    private fun formatTimestamp(timestampMs: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(timestampMs))
}
