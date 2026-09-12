package com.miruplay.tv.measure

import com.miruplay.tv.audio.BiquadDesigner
import com.miruplay.tv.model.AudioDspBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Tests for the completed measurement-core surface: drift estimation +
 * dual-sweep fail-closed gate, the RoomMeasurer pipeline, WAV offline import,
 * and a full metric printout for the prototype comparison report.
 */
class RoomMeasurementTest {

    companion object {
        private const val FS = 48_000

        private val roomBands = listOf(
            Triple(42.0, +8.5, 7.0),
            Triple(68.0, +6.0, 5.5),
            Triple(90.0, -13.0, 9.0),
            Triple(115.0, +5.0, 4.5),
            Triple(160.0, -4.0, 2.5),
            Triple(8000.0, -4.5, 0.8),
        )

        /** Sweep + drifted, independently-noised recording for one sweep. */
        private class Take(val sweep: LogSweep, val recording: DoubleArray)

        private fun makeTake(sweepDurationS: Double, ppm: Double, seed: Long, roomIr: DoubleArray): Take {
            val sweep = LogSweepGenerator.generate(10.0, 20_000.0, sweepDurationS, FS)
            val rec = Fft.fftConvolve(MeasurementOps.applyDrift(sweep.sweep, ppm), roomIr)
            val rng = Random(seed)
            val noiseAmp = rec.maxOf { abs(it) } * 10.0.pow(-45.0 / 20.0)
            for (i in rec.indices) rec[i] += rng.nextGaussian() * noiseAmp
            return Take(sweep, rec)
        }

        private val roomIr by lazy {
            val delta = DoubleArray(FS)
            delta[240] = 1.0 // 5 ms direct delay
            var y = delta
            for ((f0, g, q) in roomBands) {
                val c = BiquadDesigner.design(
                    AudioDspBand(frequencyHz = f0.toFloat(), gainDb = g.toFloat(), q = q.toFloat()),
                    FS,
                )
                val out = DoubleArray(y.size)
                var z1 = 0.0
                var z2 = 0.0
                for (i in y.indices) {
                    val o = c.b0 * y[i] + z1
                    z1 = c.b1 * y[i] - c.a1 * o + z2
                    z2 = c.b2 * y[i] - c.a2 * o
                    out[i] = o
                }
                y = out
            }
            val m = y.max()
            for (i in y.indices) y[i] /= m
            y
        }

        private val irLen = FS

        private val fitConfig = PeqFitConfig(
            matchLoHz = 20.0,
            matchHiHz = 300.0,
            flatnessTargetDb = 1.0,
            maxFilters = 10,
            individualMaxBoostDb = 0.0,
            overallMaxBoostDb = 0.0,
            allowNarrowBelow200 = true,
            varyMaxQAbove200 = true,
            dropSmallFilters = true,
            trimToCrossings = true,
        )

        /** Dual-sweep (4.0 s + 4.17 s) measurement at a given true drift. */
        private val dualSweepRuns by lazy {
            mapOf(
                0.0 to listOf(makeTake(4.0, 0.0, 11, roomIr), makeTake(4.17, 0.0, 12, roomIr)),
                2.0 to listOf(makeTake(4.0, 2.0, 21, roomIr), makeTake(4.17, 2.0, 22, roomIr)),
                5.0 to listOf(makeTake(4.0, 5.0, 31, roomIr), makeTake(4.17, 5.0, 32, roomIr)),
                10.0 to listOf(makeTake(4.0, 10.0, 41, roomIr), makeTake(4.17, 10.0, 42, roomIr)),
            ).mapValues { (_, takes) ->
                val m = RoomMeasurer.measure(
                    recordings = takes.map { it.recording },
                    sweeps = takes.map { it.sweep },
                    fs = FS,
                )
                m to MeasurementOps.cosineSimilarity(m.ir, roomIr.copyOf(irLen))
            }
        }
    }

    @Test
    fun `drift estimator recovers small drift with sub-sample accuracy`() {
        val take = makeTake(4.0, 2.0, 101, roomIr)
        val est = DriftEstimator.estimate(take.recording, take.sweep.sweep, irLen)
        // Prototype baseline: true 2 ppm → est 2.20 ppm. ≥5 ppm can land on
        // spurious lattice peaks (documented) — that's what the gate is for.
        assertTrue("est ${est.estimatedPpm} for true 2 ppm", abs(est.estimatedPpm - 2.0) <= 1.5)
    }

    @Test
    fun `drift estimator finds zero drift when clocks agree`() {
        val take = makeTake(4.0, 0.0, 102, roomIr)
        val est = DriftEstimator.estimate(take.recording, take.sweep.sweep, irLen)
        assertTrue("est ${est.estimatedPpm} for true 0 ppm", abs(est.estimatedPpm) <= 1.5)
    }

    @Test
    fun `dual sweep fail-closed gate never approves a collapsed measurement`() {
        for ((truePpm, pair) in dualSweepRuns) {
            val (m, compensatedCs) = pair
            println(
                "dual-sweep true=%.1f ppm: est=%.2f estimates=%s spread=%.2f consistent=%s valid=%s compensatedCs=%.3f"
                    .format(
                        truePpm, m.drift.estimatedPpm, m.drift.estimates.map { "%.2f".format(it) },
                        m.drift.ppmSpread, m.drift.consistent, m.valid, compensatedCs,
                    ),
            )
            // The core safety property: an approved measurement is a good measurement.
            if (m.valid) {
                assertTrue(
                    "valid measurement at true $truePpm ppm has compensated cs $compensatedCs",
                    compensatedCs >= 0.85,
                )
                assertTrue(m.drift.consistent)
            }
        }
        // 5 ppm dual run: estimates agree, compensation recovers the room (measured,
        // deterministic for these seeds) — this is the documented usable path.
        val m5 = dualSweepRuns.getValue(5.0).first
        assertTrue(m5.valid)
        // 0/2 ppm dual runs may be rejected (estimates land on different lattice
        // cells — documented false negative, fail-closed is correct behavior).
        val m0 = dualSweepRuns.getValue(0.0).first
        if (!m0.valid) assertTrue(m0.invalidReason!!.contains("disagree"))
    }

    @Test
    fun `room measurer rejects single sweep as drift-unverifiable`() {
        val take = makeTake(4.0, 0.0, 51, roomIr)
        val m = RoomMeasurer.measure(
            recordings = listOf(take.recording),
            sweeps = listOf(take.sweep),
            fs = FS,
        )
        assertFalse(m.valid)
        assertTrue(m.invalidReason!!.contains("dual sweep"))
    }

    @Test
    fun `room measurer end to end on an accepted dual sweep`() {
        // Deterministic for these seeds: the 5 ppm dual run passes the gate and
        // compensates correctly (compensatedCs ≈ 0.93).
        val m = dualSweepRuns.getValue(5.0).first
        assertTrue(m.valid)
        assertTrue(m.fit.bands.isNotEmpty())
        assertTrue(m.fit.bands.size <= 10)
        assertTrue(m.fit.bands.all { it.gainDb <= 0f })
        // The 90 Hz null must not be boosted.
        assertTrue(m.fit.bands.none { it.frequencyHz in 75f..105f && it.gainDb > 0.5f })
    }

    @Test
    fun `shared clock wav import skips the drift gate and stays valid`() {
        val take = makeTake(4.0, 0.0, 61, roomIr)
        val m = RoomMeasurer.measure(
            recordings = listOf(take.recording),
            sweeps = listOf(take.sweep),
            fs = FS,
            assumeSharedClock = true,
        )
        assertTrue(m.valid)
        assertTrue(m.fit.bands.isNotEmpty())
    }

    @Test
    fun `wav round trip preserves samples and sample rate`() {
        val samples = DoubleArray(1000) { kotlin.math.sin(it * 0.01) * 0.5 }
        val file = File.createTempFile("miruplay-measure", ".wav")
        try {
            WavFile.write16bitMono(file.absolutePath, samples, FS)
            val (read, fs) = WavFile.read16bitMono(file.absolutePath)
            assertEquals(FS.toLong(), fs.toLong())
            assertEquals(samples.size.toLong(), read.size.toLong())
            // 16-bit quantization: ≤ ~1 LSB per sample
            for (i in samples.indices) {
                if (abs(samples[i]) < 0.999) {
                    assertTrue(abs(samples[i] - read[i]) <= 1.1 / 32768)
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `wav reader rejects malformed input`() {
        val file = File.createTempFile("miruplay-measure-bad", ".wav")
        try {
            file.writeBytes(ByteArray(64))
            var threw = false
            try {
                WavFile.read16bitMono(file.absolutePath)
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue(threw)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `comparison report - prints all pipeline metrics for prototype diff`() {
        // Mirror the prototype's exact path at 0 drift: deconvolve → response →
        // smoothing → target → fit (no drift machinery).
        val take = makeTake(4.0, 0.0, 51, roomIr)
        val ir = Deconvolver.deconvolve(take.recording, take.sweep.sweep).copyOf(irLen)
        val freqs = ResponseAnalysis.logFreqGrid(10.0, 24_000.0, 48)
        val responseDb = ResponseAnalysis.irToResponseDb(ir, freqs, FS)
        val center = responseDb.meanWhere(BooleanArray(freqs.size) { freqs[it] in 200.0..2000.0 })
        for (i in freqs.indices) responseDb[i] -= center
        val smoothed = ResponseAnalysis.smoothGaussianLogf(freqs, responseDb, ResponseAnalysis.smoothingWidthVariable(freqs))
        val level = ResponseAnalysis.autoTargetLevel(smoothed, freqs)
        val target = ResponseAnalysis.buildTarget(
            freqs, level,
            lfCutoffHz = 15.0, lfSlopeDbOct = 24.0,
            hfFallStartHz = 10_000.0, hfFallDbOct = 1.5,
        )
        val fit = AutoEqFitter.fitPeq(smoothed, target, freqs, fitConfig, FS)
        val truth = DoubleArray(freqs.size)
        for ((f0, g, q) in roomBands) {
            val resp = AutoEqFitter.bandResponseDb(
                AudioDspBand(frequencyHz = f0.toFloat(), gainDb = g.toFloat(), q = q.toFloat()),
                freqs, FS,
            )
            for (i in freqs.indices) truth[i] += resp[i]
        }
        val centerMask = BooleanArray(freqs.size) { freqs[it] in 200.0..2000.0 }
        val bandMask = BooleanArray(freqs.size) { freqs[it] in 20.0..20_000.0 }
        val truthCentered = truth.copyOf()
        val tc = truthCentered.meanWhere(centerMask)
        for (i in freqs.indices) truthCentered[i] -= tc
        var rawSq = 0.0
        var smoothSq = 0.0
        var n = 0
        for (i in freqs.indices) {
            if (bandMask[i]) {
                val d1 = responseDb[i] - truthCentered[i]
                val d2 = smoothed[i] - truthCentered[i]
                rawSq += d1 * d1
                smoothSq += d2 * d2
                n++
            }
        }
        val cosSim = MeasurementOps.cosineSimilarity(ir, roomIr.copyOf(irLen))
        val peakSample = ir.indices.maxBy { abs(ir[it]) }
        val (peakAfter, nullResidual) = AutoEqFitter.finalMetrics(
            smoothed, target, fit.bands, freqs, FS, fit.matchLoHz, fit.matchHiHz,
        )
        var peakBefore = 0.0
        for (i in freqs.indices) {
            if (freqs[i] in fit.matchLoHz..fit.matchHiHz) {
                peakBefore = maxOf(peakBefore, smoothed[i] - target[i])
            }
        }

        println("=== KOTLIN COMPARISON REPORT ===")
        println("cosSim ${"%.4f".format(cosSim)}  peakSampleMs ${"%.2f".format(peakSample / FS * 1000.0)}")
        println("rawRmsDb ${"%.3f".format(sqrt(rawSq / n))}")
        println("smoothedRmsDb ${"%.3f".format(sqrt(smoothSq / n))}")
        println("matchRangeHz ${"%.1f".format(fit.matchLoHz)} - ${"%.1f".format(fit.matchHiHz)}")
        println("bands ${fit.bands.size}")
        for (b in fit.bands.sortedBy { it.frequencyHz }) {
            println(
                "band ${"%.1f".format(b.frequencyHz.toDouble())} ${"%.2f".format(b.gainDb.toDouble())} " +
                    "Q${"%.2f".format(b.q.toDouble())} t60ms ${"%.1f".format(AutoEqFitter.t60OfFilter(b.frequencyHz.toDouble(), b.q.toDouble()) * 1000)}",
            )
        }
        println("fitPeakBeforeDb ${"%.2f".format(peakBefore)}")
        println("fitPeakAfterDb ${"%.2f".format(peakAfter)}")
        println("fitNullResidualDb ${"%.2f".format(nullResidual)}")
        println("fitRmsDevDb ${"%.2f".format(fit.rmsDeviationDb)}")
        for ((truePpm, pair) in dualSweepRuns) {
            val (mm, cs) = pair
            println(
                "drift true=$truePpm est=${"%.2f".format(mm.drift.estimatedPpm)} " +
                    "spread=${"%.2f".format(mm.drift.ppmSpread)} valid=${mm.valid} compensatedCs=${"%.4f".format(cs)}",
            )
        }
        println("=== END COMPARISON REPORT ===")

        assertTrue(fit.bands.isNotEmpty())
    }

    private fun DoubleArray.meanWhere(mask: BooleanArray): Double {
        var sum = 0.0
        var n = 0
        for (i in indices) if (mask[i]) {
            sum += this[i]; n++
        }
        return sum / n
    }
}
