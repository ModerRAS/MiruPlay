@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.miruplay.tv.audio.AudioDspPlanCompiler
import com.miruplay.tv.audio.ChannelLayout
import com.miruplay.tv.audio.CompiledDspPlan
import com.miruplay.tv.audio.StreamingDspProcessor
import com.miruplay.tv.model.AudioDspConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DspAudioProcessor(
    private val runtimeConfig: AudioDspRuntimeConfig,
) : BaseAudioProcessor() {
    private var processor: StreamingDspProcessor? = null
    private var compiledPlan: CompiledDspPlan? = null
    private var channels: Int = 0
    private var encoding: Int = C.ENCODING_INVALID
    private var sampleRateHz: Int = 0
    private var compiledRevision: Long = -1L
    private var configuredPcm = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val snapshot = runtimeConfig.snapshot()
        val isPcm = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!isPcm && !snapshot.config.enabled) {
            configuredPcm = false
            return inputAudioFormat
        }
        if (!isPcm) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        sampleRateHz = inputAudioFormat.sampleRate
        configuredPcm = true
        compiledRevision = snapshot.revision
        if (!snapshot.config.enabled) {
            compiledPlan = null
            processor = null
            return inputAudioFormat
        }
        val layout = ChannelLayout.from(channels, null)
        val preset = presetFor(snapshot.config)
        compiledPlan = AudioDspPlanCompiler.compile(preset, layout, sampleRateHz)
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

    override fun isActive(): Boolean = configuredPcm && processor != null && super.isActive()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val snapshot = runtimeConfig.snapshot()
        refreshRuntimePlan(snapshot)
        if (processor == null && snapshot.config.enabled) {
            activateRuntimePlan(snapshot)
        }
        val active = processor ?: run {
            passThrough(inputBuffer)
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
        refreshRuntimePlan(runtimeConfig.snapshot())
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
        sampleRateHz = 0
        compiledRevision = -1L
        configuredPcm = false
    }

    private fun refreshRuntimePlan(snapshot: AudioDspRuntimeConfig.Snapshot) {
        val active = processor ?: return
        if (snapshot.revision == compiledRevision) return
        if (!snapshot.config.enabled) {
            val currentPlan = compiledPlan ?: return
            if (currentPlan.outputChannelCount == channels) {
                processor = null
                compiledPlan = null
                compiledRevision = snapshot.revision
            }
            return
        }
        val nextPlan = AudioDspPlanCompiler.compile(
            presetFor(snapshot.config),
            ChannelLayout.from(channels, null),
            sampleRateHz,
        )
        val currentPlan = compiledPlan ?: return
        if (nextPlan.outputChannelCount != currentPlan.outputChannelCount) {
            // A channel-count change requires sink reconfiguration. Keep the revision
            // pending so a subsequent session or configure call cannot lose it.
            return
        }
        active.queuePlan(nextPlan)
        compiledPlan = nextPlan
        compiledRevision = snapshot.revision
    }

    private fun activateRuntimePlan(snapshot: AudioDspRuntimeConfig.Snapshot) {
        if (!configuredPcm || !snapshot.config.enabled) return
        val nextPlan = AudioDspPlanCompiler.compile(
            presetFor(snapshot.config),
            ChannelLayout.from(channels, null),
            sampleRateHz,
        )
        if (nextPlan.outputChannelCount != channels) return
        compiledPlan = nextPlan
        processor = StreamingDspProcessor(nextPlan)
        compiledRevision = snapshot.revision
    }

    private fun presetFor(config: AudioDspConfig) =
        config.presets.firstOrNull { it.id == config.selectedPresetId }
            ?: config.presets.first()

    private fun passThrough(inputBuffer: ByteBuffer) {
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameSize = channels * bytesPerSample
        if (frameSize <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val byteCount = inputBuffer.remaining() - (inputBuffer.remaining() % frameSize)
        val output = replaceOutputBuffer(byteCount)
        val source = inputBuffer.duplicate()
        source.limit(source.position() + byteCount)
        output.put(source)
        inputBuffer.position(inputBuffer.position() + byteCount)
        output.flip()
    }
}
