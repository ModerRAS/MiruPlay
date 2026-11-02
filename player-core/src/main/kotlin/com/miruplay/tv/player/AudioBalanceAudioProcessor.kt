@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.miruplay.tv.audio.LoudnessBalanceProcessor
import com.miruplay.tv.model.AudioBalanceLevel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Exo/Media3 hook for [LoudnessBalanceProcessor]. Sits after [DspAudioProcessor] and before
 * optional [MusicSrcBypassProcessor] so EQ/downmix/limiter run first, then loudness balance,
 * then SRC to the device mixer rate.
 */
class AudioBalanceAudioProcessor(
    private val runtimeConfig: AudioDspRuntimeConfig,
) : BaseAudioProcessor() {
    private var balance: LoudnessBalanceProcessor? = null
    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var sampleRateHz = 0
    private var appliedRevision = -1L
    private var appliedLevel = AudioBalanceLevel.OFF
    private var configuredPcm = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val isPcm = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!isPcm) {
            configuredPcm = false
            balance = null
            return inputAudioFormat
        }
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        sampleRateHz = inputAudioFormat.sampleRate
        configuredPcm = true
        syncProcessor(forceReset = true)
        return inputAudioFormat
    }

    override fun isActive(): Boolean =
        configuredPcm && appliedLevel.isEnabled && balance != null && super.isActive()

    override fun queueInput(inputBuffer: ByteBuffer) {
        syncProcessor(forceReset = false)
        val active = balance
        if (active == null || !appliedLevel.isEnabled) {
            passThrough(inputBuffer)
            return
        }
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameSize = channels * bytesPerSample
        val byteCount = inputBuffer.remaining() - (inputBuffer.remaining() % frameSize)
        val frames = byteCount / frameSize
        if (frames == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
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
        active.processInPlace(samples, channels)
        val output = replaceOutputBuffer(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        var sampleIndex = 0
        repeat(frames * channels) {
            if (encoding == C.ENCODING_PCM_FLOAT) {
                output.putFloat(samples[sampleIndex++])
            } else {
                output.putShort((samples[sampleIndex++].coerceIn(-1f, 1f) * 32_767f).toInt().toShort())
            }
        }
        output.flip()
    }

    override fun onFlush() {
        balance?.resetState()
    }

    override fun onReset() {
        balance = null
        channels = 0
        encoding = C.ENCODING_INVALID
        sampleRateHz = 0
        appliedRevision = -1L
        appliedLevel = AudioBalanceLevel.OFF
        configuredPcm = false
    }

    private fun syncProcessor(forceReset: Boolean) {
        val snapshot = runtimeConfig.snapshot()
        val level = snapshot.audioBalanceLevel
        if (!forceReset && snapshot.revision == appliedRevision && level == appliedLevel) return
        appliedRevision = snapshot.revision
        appliedLevel = level
        if (!configuredPcm || !level.isEnabled) {
            balance = null
            return
        }
        val existing = balance
        if (existing == null || forceReset) {
            balance = LoudnessBalanceProcessor(level, sampleRateHz)
        } else {
            existing.setLevel(level, sampleRateHz)
        }
    }

    private fun passThrough(inputBuffer: ByteBuffer) {
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameSize = channels * bytesPerSample
        if (frameSize <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val byteCount = inputBuffer.remaining() - (inputBuffer.remaining() % frameSize)
        if (byteCount <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val output = replaceOutputBuffer(byteCount)
        val source = inputBuffer.duplicate()
        source.limit(source.position() + byteCount)
        output.put(source)
        inputBuffer.position(inputBuffer.position() + byteCount)
        output.flip()
    }
}
