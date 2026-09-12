package com.miruplay.tv.measure

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/** Frequency-response analysis utilities: log grid, IR→response, REW variable smoothing, target curve. */
object ResponseAnalysis {

    fun logFreqGrid(fLo: Double, fHi: Double, perOctave: Int): DoubleArray {
        val octaves = log2(fHi / fLo)
        val n = (octaves * perOctave).toInt() + 1
        return DoubleArray(n) { fLo * 2.0.pow(it / perOctave.toDouble()) }
    }

    /** Magnitude response (dB) of an impulse response sampled onto [freqs] (linear interp). */
    fun irToResponseDb(ir: DoubleArray, freqs: DoubleArray, fs: Int): DoubleArray {
        val nFft = Fft.nextPow2(ir.size)
        val m = nFft / 2 + 1
        val (hRe, hIm) = Fft.rfft(ir, nFft)
        val fAxis = DoubleArray(m) { it * fs.toDouble() / nFft }
        val magDb = DoubleArray(m) {
            val mag = kotlin.math.sqrt(hRe[it] * hRe[it] + hIm[it] * hIm[it])
            20.0 * kotlin.math.log10(kotlin.math.max(mag, 1e-12))
        }
        return DoubleArray(freqs.size) { interp(freqs[it], fAxis, magDb) }
    }

    /**
     * REW variable smoothing bandwidth in octaves: 1/48 oct below 100 Hz,
     * 1/3 oct above 10 kHz, geometric transition reaching 1/6 oct at 1 kHz.
     */
    fun smoothingWidthVariable(freqs: DoubleArray): DoubleArray = DoubleArray(freqs.size) { i ->
        val f = freqs[i]
        when {
            f <= 100.0 -> 1.0 / 48.0
            f >= 10_000.0 -> 1.0 / 3.0
            f <= 1_000.0 -> (1.0 / 48.0) * 8.0.pow(log2(f / 100.0) / log2(10.0))
            else -> (1.0 / 6.0) * 2.0.pow(log2(f / 1000.0) / log2(10.0))
        }
    }

    /**
     * Gaussian smoothing on the log-frequency axis. [widthOctaves] is interpreted
     * as the Gaussian FWHM (σ = FWHM / 2.3548); REW internally approximates the
     * same shape with bidirectional one-pole IIR passes.
     */
    fun smoothGaussianLogf(freqs: DoubleArray, db: DoubleArray, widthOctaves: DoubleArray): DoubleArray {
        val n = freqs.size
        val lf = DoubleArray(n) { log2(freqs[it]) }
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val sigma = widthOctaves[i] / 2.3548
            var acc = 0.0
            var wsum = 0.0
            for (j in 0 until n) {
                val d = (lf[j] - lf[i]) / sigma
                if (abs(d) > 4.0) continue // 4σ cutoff
                val k = kotlin.math.exp(-0.5 * d * d)
                acc += k * db[j]
                wsum += k
            }
            out[i] = if (wsum <= 0.0) db[i] else acc / wsum
        }
        return out
    }

    /**
     * REW Target Settings full-range shape: flat down to the LF cutoff then
     * rolling off, optional room curve (LF rise + HF fall), shifted to [targetLevelDb].
     */
    fun buildTarget(
        freqs: DoubleArray,
        targetLevelDb: Double,
        lfCutoffHz: Double = 40.0,
        lfSlopeDbOct: Double = 24.0,
        hfFallStartHz: Double = 10_000.0,
        hfFallDbOct: Double = 0.0,
        lfRiseStartHz: Double = 200.0,
        lfRiseEndHz: Double = 50.0,
        lfRiseDb: Double = 0.0,
    ): DoubleArray {
        val ln10 = kotlin.math.ln(10.0)
        val t = DoubleArray(freqs.size) { targetLevelDb }
        for (i in freqs.indices) {
            val f = freqs[i]
            if (lfCutoffHz > 0 && f < lfCutoffHz) {
                t[i] -= lfSlopeDbOct * log2(lfCutoffHz / kotlin.math.max(f, 1e-6))
            }
            if (hfFallDbOct > 0 && f > hfFallStartHz) {
                t[i] -= hfFallDbOct * log2(f / hfFallStartHz)
            }
        }
        if (lfRiseDb > 0 && lfRiseStartHz > lfRiseEndHz) {
            val denom = log2(lfRiseStartHz / lfRiseEndHz)
            for (i in freqs.indices) {
                val f = freqs[i]
                if (f < lfRiseStartHz) {
                    val frac = (log2(lfRiseStartHz / kotlin.math.max(f, 1e-6)) / denom).coerceIn(0.0, 1.0)
                    t[i] += lfRiseDb * frac
                }
            }
        }
        return t
    }

    /** REW "Calculate target level from response": align on the 200 Hz–2 kHz mean. */
    fun autoTargetLevel(measuredDb: DoubleArray, freqs: DoubleArray, loHz: Double = 200.0, hiHz: Double = 2_000.0): Double {
        var sum = 0.0
        var count = 0
        for (i in freqs.indices) {
            if (freqs[i] in loHz..hiHz) {
                sum += measuredDb[i]
                count++
            }
        }
        return sum / count
    }

    private fun interp(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (x <= xs[0]) return ys[0]
        if (x >= xs[xs.size - 1]) return ys[xs.size - 1]
        var lo = 0
        var hi = xs.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] <= x) lo = mid else hi = mid
        }
        val frac = (x - xs[lo]) / (xs[hi] - xs[lo])
        return ys[lo] * (1.0 - frac) + ys[hi] * frac
    }
}
