package com.miruplay.tv.player

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspOutputMode
import com.miruplay.tv.model.AudioDspPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDspMpvOptionsTest {
    @Test
    fun `disabled config leaves mpv direct output untouched`() {
        assertTrue(buildAudioDspMpvOptions(AudioDspConfig.neutral()).isEmpty())
    }

    @Test
    fun `enabled config emits pcm biquad and stereo downmix options`() {
        val config = AudioDspConfig(
            enabled = true,
            presets = listOf(
                AudioDspPreset(
                    "movie",
                    "Movie",
                    outputMode = AudioDspOutputMode.STEREO_DOWNMIX,
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
        assertTrue(options.getValue("af").contains("biquad"))
        assertTrue(options.getValue("af").contains("pan=stereo"))
    }
}
