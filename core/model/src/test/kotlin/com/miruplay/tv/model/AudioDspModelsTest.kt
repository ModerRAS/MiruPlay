package com.miruplay.tv.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDspModelsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `neutral config is disabled and has one neutral preset`() {
        val config = AudioDspConfig.neutral()

        assertFalse(config.enabled)
        assertEquals(AudioDspConfig.DEFAULT_PRESET_ID, config.selectedPresetId)
        assertEquals(1, config.presets.size)
        assertTrue(config.presets.single().rules.isEmpty())
    }

    @Test
    fun `unknown storage values fall back to safe enum values`() {
        assertEquals(AudioDspPhaseMode.MINIMUM, AudioDspPhaseMode.fromStorageValue("future"))
        assertEquals(AudioDspOutputMode.AUTO_PRESERVE, AudioDspOutputMode.fromStorageValue("future"))
        assertEquals(AudioDspFirQuality.MEDIUM, AudioDspFirQuality.fromStorageValue("future"))
        assertEquals(AudioDspFilterType.PEAKING, AudioDspFilterType.fromStorageValue("future"))
    }

    @Test
    fun `normalized clamps malformed band and preamp values`() {
        val config = AudioDspConfig(
            enabled = true,
            selectedPresetId = "p",
            presets = listOf(
                AudioDspPreset(
                    id = "p",
                    name = "Bad",
                    preampDb = 99f,
                    rules = listOf(
                        AudioDspChannelRule(
                            bands = listOf(
                                AudioDspBand(
                                    type = AudioDspFilterType.PEAKING,
                                    frequencyHz = 1f,
                                    gainDb = -99f,
                                    q = 99f,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).normalized()

        val band = config.presets.single().rules.single().bands.single()
        assertEquals(12f, config.presets.single().preampDb)
        assertEquals(10f, band.frequencyHz)
        assertEquals(-24f, band.gainDb)
        assertEquals(20f, band.q)
    }

    @Test
    fun `validation rejects duplicate ids and unsafe channel and limiter values`() {
        val config = AudioDspConfig(
            selectedPresetId = "missing",
            presets = listOf(
                AudioDspPreset(
                    id = "movie",
                    name = "Movie",
                    rules = listOf(AudioDspChannelRule(outputGainDb = 25f)),
                    limiter = AudioDspLimiter(enabled = true, ceilingDb = 1f, releaseMs = 0f),
                ),
                AudioDspPreset(id = "movie", name = "Duplicate"),
            ),
        )

        val errors = config.validationErrors()

        assertTrue(errors.any { it.contains("selectedPresetId") })
        assertTrue(errors.any { it.contains("duplicate") })
        assertTrue(errors.any { it.contains("outputGainDb") })
        assertTrue(errors.any { it.contains("ceilingDb") })
        assertTrue(errors.any { it.contains("releaseMs") })
    }

    @Test
    fun `json round trip preserves 5 point 1 channel target`() {
        val original = AudioDspConfig(
            presets = listOf(
                AudioDspPreset(
                    id = "movie",
                    name = "Movie",
                    rules = listOf(
                        AudioDspChannelRule(target = AudioDspChannelTarget.SURROUND_5_1),
                    ),
                ),
            ),
            selectedPresetId = "movie",
        )

        val decoded = json.decodeFromString<AudioDspConfig>(
            json.encodeToString(AudioDspConfig.serializer(), original),
        )
        assertEquals(AudioDspChannelTarget.SURROUND_5_1, decoded.presets.single().rules.single().target)
    }
}
