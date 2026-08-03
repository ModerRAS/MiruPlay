package com.miruplay.tv.player

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspOutputMode
import com.miruplay.tv.model.AudioDspPreset
import com.miruplay.tv.model.AudioDspLimiter
import com.miruplay.tv.model.AudioDspChannelTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDspMpvOptionsTest {
    @Test
    fun `disabled config leaves mpv direct output untouched`() {
        assertTrue(buildAudioDspMpvOptions(AudioDspConfig.neutral()).isEmpty())
    }

    @Test
    fun `enabled config emits sample-rate-aware pcm filters`() {
        val config = AudioDspConfig(
            enabled = true,
            presets = listOf(
                AudioDspPreset(
                    "movie",
                    "Movie",
                    rules = listOf(
                        AudioDspChannelRule(
                            bands = listOf(AudioDspBand(AudioDspFilterType.PEAKING, 1_000f, 6f, 1f)),
                        ),
                    ),
                ),
            ),
            selectedPresetId = "movie",
        )
        val options = buildAudioDspMpvOptions(config)

        assertEquals("no", options["audio-spdif"])
        assertEquals("audiotrack", options["ao"])
        assertTrue(options.getValue("af").startsWith("lavfi=["))
        assertTrue(options.getValue("af").contains("equalizer=f="))
        assertTrue(options.getValue("af").contains(":c=all"))
    }

    @Test
    fun `mpv rejects runtime-dependent downmix and hrtf graphs`() {
        val config = AudioDspConfig(
            enabled = true,
            selectedPresetId = "surround",
            presets = listOf(
                AudioDspPreset("surround", "Surround", outputMode = AudioDspOutputMode.HRTF_BINAURAL),
            ),
        )

        assertTrue(buildAudioDspMpvOptions(config).isEmpty())
    }

    @Test
    fun `mpv options apply preamp and limiter controls`() {
        val config = AudioDspConfig(
            enabled = true,
            presets = listOf(
                AudioDspPreset(
                    "movie",
                    "Movie",
                    preampDb = -3f,
                    limiter = AudioDspLimiter(enabled = true, ceilingDb = -6f, releaseMs = 250f),
                ),
            ),
            selectedPresetId = "movie",
        )

        val filters = buildAudioDspMpvOptions(config).getValue("af")

        assertTrue(filters.contains("volume=-3.0dB"))
        assertTrue(filters.contains("alimiter=limit="))
        assertTrue(filters.contains("release=250.0"))
    }

    @Test
    fun `mpv options retain independent left and right channel selectors`() {
        val config = AudioDspConfig(
            enabled = true,
            selectedPresetId = "channels",
            presets = listOf(
                AudioDspPreset(
                    "channels",
                    "Channels",
                    rules = listOf(
                        AudioDspChannelRule(
                            target = AudioDspChannelTarget.LEFT,
                            bands = listOf(AudioDspBand(AudioDspFilterType.PEAKING, 70f, -6f, 2f)),
                        ),
                        AudioDspChannelRule(
                            target = AudioDspChannelTarget.RIGHT,
                            bands = listOf(AudioDspBand(AudioDspFilterType.PEAKING, 70f, 3f, 2f)),
                        ),
                    ),
                ),
            ),
        )

        val filters = buildAudioDspMpvOptions(config).getValue("af")

        assertTrue(filters.contains("c=FL"))
        assertTrue(filters.contains("c=FR"))
    }

    @Test
    fun `five one surround target uses back channels in ffmpeg canonical layout`() {
        val config = AudioDspConfig(
            enabled = true,
            selectedPresetId = "surround",
            presets = listOf(
                AudioDspPreset(
                    "surround",
                    "Surround",
                    rules = listOf(
                        AudioDspChannelRule(
                            target = AudioDspChannelTarget.SURROUND_5_1,
                            bands = listOf(AudioDspBand(gainDb = 1f)),
                        ),
                    ),
                ),
            ),
        )

        val filters = buildAudioDspMpvOptions(config).getValue("af")

        assertTrue(filters.contains("c=BL+BR"))
        assertTrue(!filters.contains("c=SL+SR"))
    }

    @Test
    fun `linear mpv options use the core response and common fir delay`() {
        val config = AudioDspConfig(
            enabled = true,
            selectedPresetId = "linear",
            presets = listOf(
                AudioDspPreset(
                    "linear",
                    "Linear",
                    phaseMode = com.miruplay.tv.model.AudioDspPhaseMode.LINEAR,
                    firQuality = com.miruplay.tv.model.AudioDspFirQuality.LOW,
                    rules = listOf(
                        AudioDspChannelRule(
                            bands = listOf(AudioDspBand(AudioDspFilterType.PEAKING, 1_000f, 6f, 1f)),
                        ),
                    ),
                ),
            ),
        )

        val filters = buildAudioDspMpvOptions(config).getValue("af")

        assertTrue(filters.contains("firequalizer"))
        assertTrue(filters.contains("delay="))
        assertTrue(filters.contains("gain_entry='"))
        assertTrue(filters.length > 500)
    }

    @Test
    fun `linear channel-specific rules are rejected for mpv so Exo keeps per-channel FIR`() {
        val config = AudioDspConfig(
            enabled = true,
            selectedPresetId = "linear-channels",
            presets = listOf(
                AudioDspPreset(
                    "linear-channels",
                    "Linear channels",
                    phaseMode = com.miruplay.tv.model.AudioDspPhaseMode.LINEAR,
                    rules = listOf(
                        AudioDspChannelRule(target = AudioDspChannelTarget.LEFT, bands = listOf(AudioDspBand(gainDb = 3f))),
                        AudioDspChannelRule(target = AudioDspChannelTarget.RIGHT, bands = listOf(AudioDspBand(gainDb = -3f))),
                    ),
                ),
            ),
        )

        assertTrue(!isAudioDspMpvCompatible(config))
        assertTrue(buildAudioDspMpvOptions(config).isEmpty())
    }

    @Test
    fun `ijk options expose only the native ffmpeg audio filter contract`() {
        val config = AudioDspConfig(
            enabled = true,
            presets = listOf(AudioDspPreset("ijk", "IJK")),
            selectedPresetId = "ijk",
        )

        val options = buildAudioDspIjkOptions(config)

        assertTrue(options.containsKey("af"))
        assertTrue(!options.getValue("af").startsWith("lavfi=["))
        assertEquals("f32", options["audio-format"])
        assertEquals("0", options["audio-spdif"])
        assertTrue(!options.containsKey("ao"))
    }
}
