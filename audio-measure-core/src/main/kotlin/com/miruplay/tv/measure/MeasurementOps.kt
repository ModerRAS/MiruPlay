package com.miruplay.tv.measure

import kotlin.math.abs
import kotlin.math.sqrt

/** Clock-drift simulation and measurement-quality metrics (prototype §2 / T7). */
object MeasurementOps {

    /**
     * Simulate a capture clock that deviates from the playback clock by [ppm]:
     * resample [x] with sample spacing (1 + ppm·1e-6) via linear interpolation.
     */
    fun applyDrift(x: DoubleArray, ppm: Double): DoubleArray {
        if (ppm == 0.0) return x.copyOf()
        val n = x.size
        val out = DoubleArray(n)
        val scale = 1.0 + ppm * 1e-6
        for (i in 0 until n) {
            val idx = i * scale
            val i0 = idx.toInt()
            val frac = idx - i0
            out[i] = if (i0 >= n - 1) x[n - 1] else x[i0] * (1.0 - frac) + x[i0 + 1] * frac
        }
        return out
    }

    /** Peak concentration: max|IR| / rms(IR). Drift spreads energy and lowers it. */
    fun irSharpness(ir: DoubleArray): Double {
        var sumSq = 0.0
        var maxAbs = 0.0
        for (v in ir) {
            sumSq += v * v
            val a = abs(v)
            if (a > maxAbs) maxAbs = a
        }
        val rms = sqrt(sumSq / ir.size) + 1e-30
        return maxAbs / rms
    }

    fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        val n = minOf(a.size, b.size)
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return dot / (sqrt(na) * sqrt(nb) + 1e-30)
    }
}
