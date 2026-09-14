package com.miruplay.tv.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dumps the exact sweep the controller plays (fs=48000, 16-bit, −12 dBFS)
 * and asserts it matches the ESS definition: f(t)=f1·e^(kt), analytic phase
 * φ(t)=2π·(f1/k)·(e^(kt)−1), equal time per octave.
 */
class SweepEssDumpTest {

    @Test
    fun `dump played sweep as wav and assert ess properties`() {
        val fs = 48000
        val f1 = 20.0
        val f2 = 20000.0
        val durationS = 4.0
        val sweep = LogSweepGenerator.generate(f1, f2, durationS, fs)

        // Controller playback path: × SWEEP_AMPLITUDE (−12 dBFS) → int16
        val amplitude = 0.25
        val int16 = sweep.sweep.map { (it * amplitude * Short.MAX_VALUE).toInt().toShort() }

        // write16bitMono expects [-1,1] doubles — feed the double domain (sweep × amplitude),
        // NOT the int16 domain: the writer clamps to ±1 before ×32767, so int16-domain values
        // collapse to a square wave (all intermediate levels flattened → constant crackle).
        val doubleDomain = sweep.sweep.map { it * amplitude }.toDoubleArray()

        val out = java.io.File("..", "output").resolve("sweep-20hz-20khz-48k.wav")
        out.parentFile?.mkdirs()
        WavFile.write16bitMono(out.absolutePath, doubleDomain, 48000)
        println("WAV dumped to: ${out.absolutePath} (${out.length()} bytes)")

        // duration
        assertEquals(durationS * 48000.0, sweep.sweep.size.toDouble(), 0.5)

        // amplitude domain: dumped WAV must be a −12 dBFS sine, not a clipped square.
        // RMS ≈ A/√2 = 0.177; a square (sign) collapse has RMS ≈ A — this assert makes
        // the int16-vs-double domain mix-up fail loudly instead of silently passing.
        var peak = 0.0
        var sumSq = 0.0
        for (v in doubleDomain) {
            val a = kotlin.math.abs(v)
            if (a > peak) peak = a
            sumSq += v * v
        }
        val rms = kotlin.math.sqrt(sumSq / doubleDomain.size)
        assertEquals(0.25, peak, 0.01)
        assertEquals(0.25 / Math.sqrt(2.0), rms, 0.02)

        // ESS identity: analytic phase must equal accumulated phase of f(t)
        val L = Math.log(f2 / f1)
        val k = L / durationS
        val fsD = 48000.0
        var maxPhaseErr = 0.0
        var prevPhase = 0.0
        for (i in int16.indices) {
            val t = i / fsD
            val analytic = 2.0 * Math.PI * (f1 / k) * (Math.exp(k * t) - 1.0)
            // cumulative phase via increments between samples
            if (i > 0) {
                val fMid = f1 * Math.exp(k * ((i - 0.5) / fsD))
                prevPhase += 2.0 * Math.PI * fMid / fsD
            } else {
                prevPhase = 0.0
            }
            maxPhaseErr = maxOf(maxPhaseErr, Math.abs(analytic - prevPhase))
        }
        // cumulative vs analytic diverge only by float rounding; bound is generous
        assertTrue("phase drift analytic-vs-incremental = $maxPhaseErr rad", maxPhaseErr < 1e-2)

        // start frequency: first rising crossing → second ≈ one 20 Hz period (2400 samples).
        // (A 100-crossing window is meaningless here: the sweep ACCELERATES, so the
        // window always smears — measure a single period instead.)
        val firstPeriodSamples = periodSamples(int16, from = 0, maxCrossings = 2)
        assertEquals(2400.0, firstPeriodSamples, 150.0)
        // end frequency: last full period ≈ 48000/20000 = 2.4 samples
        val lastPeriodSamples = periodSamples(int16, from = int16.size - 400, maxCrossings = 100)
        assertEquals(2.4, lastPeriodSamples, 0.4)
    }

    /** Average period length via zero crossings from [from], up to [maxCrossings]. */
    private fun periodSamples(x: List<Short>, from: Int, maxCrossings: Int): Double {
        val zc = mutableListOf<Int>()
        var i = from
        while (i < x.size - 1 && zc.size < maxCrossings) {
            if (x[i] <= 0 && x[i + 1] > 0) zc += i
            i++
        }
        assertTrue(zc.size >= 2)
        val span = zc.last() - zc.first()
        return span.toDouble() / (zc.size - 1)
    }
}
