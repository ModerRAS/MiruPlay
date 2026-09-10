package com.miruplay.tv.measure

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.AudioDspPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasuredPresetFactoryTest {

    private val bands = listOf(
        AudioDspBand(frequencyHz = 41.8f, gainDb = -8.75f, q = 5.6f),
        AudioDspBand(frequencyHz = 68.3f, gainDb = -5.48f, q = 8f),
        AudioDspBand(frequencyHz = 114.8f, gainDb = -3.42f, q = 8f),
    )

    private val valid = MeasuredPresetFactory.MeasuredBands(bands, valid = true)

    @Test
    fun `buildPreset maps fitted bands into a cut-only peaking rule`() {
        val preset = MeasuredPresetFactory.buildPreset(valid, timestampMs = 1_000L, target = AudioDspChannelTarget.ALL)
        assertEquals("measured-1000", preset.id)
        assertEquals(1, preset.rules.size)
        val rule = preset.rules.first()
        assertEquals(AudioDspChannelTarget.ALL, rule.target)
        assertEquals(0f, rule.outputGainDb, 1e-6f)
        assertEquals(3, rule.bands.size)
        rule.bands.forEach { band ->
            assertEquals(com.miruplay.tv.model.AudioDspFilterType.PEAKING, band.type)
            assertTrue(band.enabled)
        }
        assertEquals(-8.75f, rule.bands[0].gainDb, 1e-4f)
        assertEquals(5.6f, rule.bands[0].q, 1e-4f)
    }

    @Test
    fun `bands beyond the model clamp are normalized`() {
        val over = MeasuredPresetFactory.MeasuredBands(
            listOf(
                AudioDspBand(frequencyHz = 5f, gainDb = -30f, q = 200f),
                AudioDspBand(frequencyHz = 30_000f, gainDb = +10f, q = 1f),
            ),
            valid = true,
        )
        val preset = MeasuredPresetFactory.buildPreset(over, timestampMs = 1_000L)
        val b = preset.rules.first().bands
        // AudioDspBand.normalized clamps to the model bounds.
        assertEquals(AudioDspBand.MIN_FREQUENCY_HZ, b[0].frequencyHz)
        assertEquals(AudioDspBand.MIN_GAIN_DB, b[0].gainDb)
        assertEquals(AudioDspBand.MAX_Q, b[0].q)
        assertEquals(AudioDspBand.MAX_FREQUENCY_HZ, b[1].frequencyHz)
    }

    @Test
    fun `applyToConfig adds and selects the measured preset preserving everything else`() {
        val existing = AudioDspConfig.neutral().normalized()
        val enabledConfig = existing.copy(enabled = true, presets = existing.presets + AudioDspPreset(id = "custom", name = "Custom"))
        val updated = MeasuredPresetFactory.applyToConfig(enabledConfig, valid, timestampMs = 42L)

        assertTrue(updated.enabled)
        assertEquals("measured-42", updated.selectedPresetId)
        assertEquals(enabledConfig.presets.size + 1, updated.presets.size)
        assertTrue(updated.presets.any { it.id == "custom" })
    }

    @Test
    fun `applyToConfig replaces a preset with the same timestamp id`() {
        val config = MeasuredPresetFactory.applyToConfig(AudioDspConfig.neutral(), valid, timestampMs = 7L)
        val newBands = MeasuredPresetFactory.MeasuredBands(bands.take(1), valid = true)
        val updated = MeasuredPresetFactory.applyToConfig(config, newBands, timestampMs = 7L)
        assertEquals(1, updated.presets.count { it.id == "measured-7" })
        assertEquals(1, updated.presets.first { it.id == "measured-7" }.rules.first().bands.size)
    }

    @Test
    fun `invalid measurement is rejected`() {
        val invalid = MeasuredPresetFactory.MeasuredBands(bands, valid = false, invalidReason = "drift")
        assertThrows(IllegalArgumentException::class.java) {
            MeasuredPresetFactory.applyToConfig(AudioDspConfig.neutral(), invalid, timestampMs = 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MeasuredPresetFactory.buildPreset(invalid, timestampMs = 1L)
        }
    }
}
