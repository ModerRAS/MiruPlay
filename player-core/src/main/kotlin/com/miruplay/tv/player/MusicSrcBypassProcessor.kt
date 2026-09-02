@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import java.nio.ByteBuffer

/**
 * ponytail: high-quality soft SRC to native mixer rate via Sonic (Media3 built-in).
 * When input sample rate != nativeRate, resamples to nativeRate so AudioFlinger sees native and skips its low-quality Speex SRC.
 * When equal, passes through.
 */
@OptIn(UnstableApi::class)
class MusicSrcBypassProcessor(
    private val nativeSampleRateHz: Int
) : BaseAudioProcessor() {
    private val sonic = SonicAudioProcessor()
    private var isActiveBypass = false
    private var inputSampleRate = 0
    private var inputChannels = 0
    private var inputEncoding = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputSampleRate = inputAudioFormat.sampleRate
        inputChannels = inputAudioFormat.channelCount
        inputEncoding = inputAudioFormat.encoding
        if (inputSampleRate == nativeSampleRateHz || inputSampleRate == 0) {
            isActiveBypass = false
            sonic.configure(inputAudioFormat)
            return inputAudioFormat
        }
        isActiveBypass = true
        // Configure Sonic to resample to native
        sonic.setOutputSampleRateHz(nativeSampleRateHz)
        // Sonic will handle resampling; keep channels/encoding same, only rate changes
        val configured = sonic.configure(inputAudioFormat)
        // Sonic returns format with native rate
        return configured
    }

    override fun isActive(): Boolean = super.isActive || sonic.isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (isActiveBypass) {
            sonic.queueInput(inputBuffer)
            drainSonic()
        } else {
            // pass through
            val bytes = inputBuffer.remaining()
            val out = replaceOutputBuffer(bytes)
            out.put(inputBuffer)
            out.flip()
        }
    }

    override fun onQueueEndOfStream() {
        if (isActiveBypass) {
            sonic.queueEndOfStream()
            drainSonic()
        }
    }

    private fun drainSonic() {
        var out = sonic.output
        while (out.hasRemaining()) {
            val outBuf = replaceOutputBuffer(out.remaining())
            outBuf.put(out)
            outBuf.flip()
            out = sonic.output
            if (!out.hasRemaining()) break
        }
    }

    override fun onFlush() {
        sonic.flush()
        super.onFlush()
    }

    override fun onReset() {
        sonic.reset()
        super.onReset()
        isActiveBypass = false
        inputSampleRate = 0
        inputChannels = 0
        inputEncoding = C.ENCODING_INVALID
    }
}
