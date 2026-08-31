package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelRule
import com.miruplay.tv.model.AudioDspFilterType
import com.miruplay.tv.model.AudioDspOutputMode
import com.miruplay.tv.model.AudioDspPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DspAudioProcessorTest {
    @Test
    fun `enabled processor keeps pcm format and processes stereo int16`() {
        val runtime = AudioDspRuntimeConfig().also { it.update(AudioDspConfig(enabled = true)) }
        val processor = DspAudioProcessor(runtime)
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)

        processor.configure(format)
        processor.flush()
        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(8_192).putShort(-8_192)
        input.flip()
        processor.queueInput(input)
        val output = processor.output

        assertEquals(4, output.remaining())
        assertTrue(processor.isActive)
    }

    @Test
    fun `disabled pcm processor stays bit transparent and ready for runtime enable`() {
        val processor = DspAudioProcessor(AudioDspRuntimeConfig())

        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(8_192).putShort(Short.MIN_VALUE)
        input.flip()
        processor.queueInput(input)

        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertFalse(processor.isActive)
        assertEquals(8_192.toShort(), output.short)
        assertEquals(Short.MIN_VALUE, output.short)
    }

    @Test
    fun `six channel stereo downmix uses canonical fallback layout`() {
        val runtime = AudioDspRuntimeConfig().also {
            it.update(
                AudioDspConfig(
                    enabled = true,
                    selectedPresetId = "downmix",
                    presets = listOf(AudioDspPreset("downmix", "Downmix", outputMode = AudioDspOutputMode.STEREO_DOWNMIX)),
                ),
            )
        }
        val processor = DspAudioProcessor(runtime)
        val outputFormat = processor.configure(AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(32_767).putShort(0).putShort(0).putShort(0).putShort(0).putShort(0)
        input.flip()
        processor.queueInput(input)

        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, outputFormat.channelCount)
        assertTrue(output.remaining() > 0)
        assertTrue(output.short.toInt() > 20_000)
    }

    @Test
    fun `enabling dsp after pcm processor was configured disabled applies without a new sink`() {
        val runtime = AudioDspRuntimeConfig()
        val processor = DspAudioProcessor(runtime)
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)

        processor.configure(format)
        processor.flush()
        runtime.update(
            AudioDspConfig(
                enabled = true,
                selectedPresetId = "boost",
                presets = listOf(
                    AudioDspPreset(
                        id = "boost",
                        name = "Boost",
                        autoHeadroom = false,
                        rules = listOf(
                            AudioDspChannelRule(
                                outputGainDb = 6f,
                                bands = listOf(
                                    AudioDspBand(
                                        type = AudioDspFilterType.PEAKING,
                                        frequencyHz = 1_000f,
                                        gainDb = 6f,
                                        q = 1f,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(8_192).putShort(8_192)
        input.flip()
        processor.queueInput(input)

        assertTrue(processor.isActive)
        assertTrue(processor.output.order(ByteOrder.LITTLE_ENDIAN).short.toInt() > 8_192)
    }
}
