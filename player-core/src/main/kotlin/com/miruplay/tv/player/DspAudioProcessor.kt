@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.miruplay.tv.audio.AudioDspPlanCompiler
import com.miruplay.tv.audio.ChannelLayout
import com.miruplay.tv.audio.CompiledDspPlan
import com.miruplay.tv.audio.StreamingDspProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DspAudioProcessor(
    private val runtimeConfig: AudioDspRuntimeConfig,
) : BaseAudioProcessor() {
    private var processor: StreamingDspProcessor? = null
    private var compiledPlan: CompiledDspPlan? = null
    private var channels: Int = 0
    private var encoding: Int = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!runtimeConfig.config.enabled) return inputAudioFormat
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        val layout = ChannelLayout.from(channels, null)
        val preset = runtimeConfig.config.presets
            .firstOrNull { it.id == runtimeConfig.config.selectedPresetId }
            ?: runtimeConfig.config.presets.first()
        compiledPlan = AudioDspPlanCompiler.compile(preset, layout, inputAudioFormat.sampleRate)
        processor = StreamingDspProcessor(compiledPlan!!)
        return if (compiledPlan!!.outputChannelCount == inputAudioFormat.channelCount) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat(
                inputAudioFormat.sampleRate,
                compiledPlan!!.outputChannelCount,
                inputAudioFormat.encoding,
            )
        }
    }

    override fun isActive(): Boolean = runtimeConfig.config.enabled

    override fun queueInput(inputBuffer: ByteBuffer) {
        val active = processor ?: run {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameSize = channels * bytesPerSample
        val byteCount = inputBuffer.remaining() - (inputBuffer.remaining() % frameSize)
        val frames = byteCount / frameSize
        val source = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val samples = FloatArray(frames * channels)
        for (index in samples.indices) {
            samples[index] = if (encoding == C.ENCODING_PCM_FLOAT) {
                source.float
            } else {
                source.short / 32_768f
            }
        }
        inputBuffer.position(inputBuffer.position() + byteCount)
        val outputSamples = active.process(samples, frames)
        val output = replaceOutputBuffer(outputSamples.size * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
        outputSamples.forEach { sample ->
            if (encoding == C.ENCODING_PCM_FLOAT) output.putFloat(sample) else {
                output.putShort((sample.coerceIn(-1f, 1f) * 32_767f).toInt().toShort())
            }
        }
        output.flip()
    }

    override fun onQueueEndOfStream() {
        val tail = processor?.endOfStream() ?: FloatArray(0)
        if (tail.isEmpty()) return
        val output = replaceOutputBuffer(tail.size * if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        tail.forEach { sample ->
            if (encoding == C.ENCODING_PCM_FLOAT) output.putFloat(sample) else {
                output.putShort((sample.coerceIn(-1f, 1f) * 32_767f).toInt().toShort())
            }
        }
        output.flip()
    }

    override fun onFlush() {
        processor = compiledPlan?.let(::StreamingDspProcessor)
    }

    override fun onReset() {
        processor = null
        compiledPlan = null
        channels = 0
        encoding = C.ENCODING_INVALID
    }
}
