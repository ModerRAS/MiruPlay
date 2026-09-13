package com.miruplay.tv.audiomeasure

import android.content.Context
import com.miruplay.tv.measure.LogSweepGenerator
import com.miruplay.tv.measure.WavFile
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * WAV import semantics: the reference sweep length must be recovered from the
 * standard duration candidates, not from the recording length (a full-length
 * reference yields a one-sample IR). Regression for the importWav rewrite.
 */
class AudioMeasureImportWavTest {

    private val fs = AudioMeasureController.SAMPLE_RATE_HZ

    /** Simple room: direct + two early reflections (delta IR, cheap to apply). */
    private val taps: List<Pair<Int, Double>> = listOf(
        240 to 1.0,
        240 + 1200 to 0.4,
        240 + 4000 to 0.2,
    )

    private fun makeRecordingWavBytes(sweepDurationS: Double, seed: Long): ByteArray {
        val sweep = LogSweepGenerator.generate(20.0, 20_000.0, sweepDurationS, fs)
        val s = sweep.sweep
        val rec = DoubleArray(s.size + fs) // sweep + 1 s room tail
        for ((delay, gain) in taps) {
            for (i in s.indices) {
                rec[i + delay] += s[i] * 0.25 * gain
            }
        }
        val rng = Random(seed)
        val noiseAmp = rec.maxOf { abs(it) } * 10.0.pow(-45.0 / 20.0)
        for (i in rec.indices) rec[i] += (rng.nextDouble() * 2.0 - 1.0) * noiseAmp
        val file = java.io.File.createTempFile("miruplay-import", ".wav")
        try {
            WavFile.write16bitMono(file.absolutePath, rec, fs)
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    @Test
    fun `importWav recovers valid measurement from standard sweep recording`() = runBlocking {
        val controller = AudioMeasureController(mockk<Context>())
        val outcome = controller.importWav(makeRecordingWavBytes(4.0, seed = 101))
        val m = outcome.measurement
        assertTrue(m.valid)
        assertTrue(m.fit.bands.isNotEmpty())
        assertTrue(m.ir.size > fs / 2)
    }

    @Test
    fun `importWav rejects too-short recordings with a readable error`() = runBlocking {
        val controller = AudioMeasureController(mockk<Context>())
        val file = java.io.File.createTempFile("miruplay-short", ".wav")
        try {
            WavFile.write16bitMono(file.absolutePath, DoubleArray(fs * 2) { 0.0 }, fs)
            controller.importWav(file.readBytes())
        } catch (e: AudioMeasureController.MeasureException) {
            assertTrue((e.message ?: "").contains("WAV"))
            return@runBlocking
        } finally {
            file.delete()
        }
        throw AssertionError("expected MeasureException for too-short WAV")
    }
}
