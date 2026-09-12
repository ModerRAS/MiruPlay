package com.miruplay.tv.player

import androidx.media3.common.C
import com.google.common.collect.ImmutableList
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.miruplay.tv.model.AudioDspConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Regression test for the production crash: ExoPlaybackException error_code 1004
 * "Unexpected runtime error" caused by IllegalArgumentException in
 * MusicSrcBypassProcessor.queueInput (`out.put(inputBuffer)` where both buffers were the
 * shared EMPTY_BUFFER).
 *
 * Drives the real AudioProcessingPipeline (same chain DspRenderersFactory builds) the way
 * DefaultAudioSink.processBuffers does.
 */
class MusicSrcBypassPipelineTest {

    private fun makePipeline(dspEnabled: Boolean, nativeRate: Int, sampleRate: Int, channels: Int): AudioProcessingPipeline {
        val runtime = AudioDspRuntimeConfig().also { it.update(AudioDspConfig(enabled = dspEnabled)) }
        val dsp = DspAudioProcessor(runtime)
        val musicSrc = MusicSrcBypassProcessor(nativeRate)
        val chain = DefaultAudioSink.DefaultAudioProcessorChain(dsp, musicSrc)
        val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(chain.audioProcessors))
        val format = AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT)
        pipeline.configure(format)
        pipeline.flush()
        return pipeline
    }

    /** Emulates DefaultAudioSink.processBuffers: drain outputs, then feed inputBuffer until consumed. */
    private fun processBuffersEmulation(pipeline: AudioProcessingPipeline, inputBuffer: ByteBuffer) {
        val written = mutableListOf<Int>()
        while (!pipeline.isEnded) {
            var bufferToWrite: ByteBuffer
            while (true) {
                bufferToWrite = pipeline.output
                if (bufferToWrite.hasRemaining()) {
                    written.add(bufferToWrite.remaining())
                    bufferToWrite.position(bufferToWrite.limit()) // AudioTrack consumed fully
                } else break
            }
            if (!inputBuffer.hasRemaining()) break
            pipeline.queueInput(inputBuffer)
        }
    }

    private fun codecBuffer(bytes: Int, seed: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (i in 0 until bytes step 2) putShort((seed + i).toShort())
            flip()
        }

    @Test
    fun `steady playback at native rate does not crash`() {
        val pipeline = makePipeline(dspEnabled = false, nativeRate = 48_000, sampleRate = 48_000, channels = 2)
        repeat(50) { step ->
            val input = codecBuffer(4096, step)
            processBuffersEmulation(pipeline, input)
        }
        assertTrue(true)
    }

    @Test
    fun `steady playback with dsp enabled does not crash`() {
        val pipeline = makePipeline(dspEnabled = true, nativeRate = 48_000, sampleRate = 48_000, channels = 2)
        repeat(50) { step ->
            val input = codecBuffer(4096, step)
            processBuffersEmulation(pipeline, input)
        }
        assertTrue(true)
    }

    @Test
    fun `resample path 44k to 48k does not crash`() {
        val pipeline = makePipeline(dspEnabled = false, nativeRate = 48_000, sampleRate = 44_100, channels = 2)
        repeat(50) { step ->
            val input = codecBuffer(4096, step)
            processBuffersEmulation(pipeline, input)
        }
        assertTrue(true)
    }

    @Test
    fun `end of stream then drain does not crash`() {
        val pipeline = makePipeline(dspEnabled = false, nativeRate = 48_000, sampleRate = 48_000, channels = 2)
        val input = codecBuffer(8192, 7)
        processBuffersEmulation(pipeline, input)
        pipeline.queueEndOfStream()
        var guard = 0
        while (!pipeline.isEnded && guard++ < 100) {
            val b = pipeline.output
            if (b.hasRemaining()) b.position(b.limit()) else break
        }
    }

    @Test
    fun `flush and reconfigure between episodes does not crash`() {
        val pipeline = makePipeline(dspEnabled = false, nativeRate = 48_000, sampleRate = 48_000, channels = 2)
        repeat(10) { step ->
            val input = codecBuffer(4096, step)
            processBuffersEmulation(pipeline, input)
        }
        // gapless format change: reconfigure + flush (as DefaultAudioSink does on pendingConfiguration)
        pipeline.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        pipeline.flush()
        repeat(10) { step ->
            val input = codecBuffer(2048, step)
            processBuffersEmulation(pipeline, input)
        }
    }

    @Test
    fun `drain with empty buffer does not self-copy`() {
        // Reproduces the exact production sequence: getOutput() drain (processData(EMPTY_BUFFER))
        // before any real audio, where pass-through previously did out.put(EMPTY_BUFFER).
        val pipeline = makePipeline(dspEnabled = true, nativeRate = 48_000, sampleRate = 48_000, channels = 2)
        val empty = pipeline.output
        assertEquals(0, empty.remaining())
        val input = codecBuffer(4096, 1)
        processBuffersEmulation(pipeline, input)
        assertTrue(true)
    }
}
