package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.AudioDspOutputMode
import com.miruplay.tv.model.AudioDspPreset
import org.junit.Assert.assertEquals
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
    fun `disabled processor remains inactive`() {
        val processor = DspAudioProcessor(AudioDspRuntimeConfig())

        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))

        assertTrue(!processor.isActive)
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
}
