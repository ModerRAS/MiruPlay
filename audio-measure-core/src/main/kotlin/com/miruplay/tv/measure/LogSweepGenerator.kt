package com.miruplay.tv.measure

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

class LogSweep(val sweep: DoubleArray, val inverse: DoubleArray)

object LogSweepGenerator {
    /**
     * Farina logarithmic sine sweep.
     *
     * The inverse filter is the time-reversed sweep with an amplitude envelope
     * proportional to the instantaneous frequency f(t). That is a +6 dB/oct
     * time-domain envelope compensating the sweep's −3 dB/oct spectrum — an
     * envelope ∝ √f here is the classic bug that leaves a 20 dB in-band tilt.
     */
    fun generate(f1: Double, f2: Double, durationS: Double, fs: Int): LogSweep {
        val n = (durationS * fs).toInt()
        val T = durationS
        val w1 = 2.0 * PI * f1
        val w2 = 2.0 * PI * f2
        val L = ln(w2 / w1)

        val sweep = DoubleArray(n)
        for (i in 0 until n) {
            val t = i / fs.toDouble()
            sweep[i] = sin(w1 * T / L * (exp(t / T * L) - 1.0))
        }

        val inverse = DoubleArray(n)
        var maxAbs = 0.0
        for (i in 0 until n) {
            val tRev = (n - 1 - i) / fs.toDouble()
            val envelope = exp(tRev / T * L) // ∝ f(t): f(t) = f1·exp(t/T·L), f1 constant dropped
            val v = sweep[n - 1 - i] * envelope
            inverse[i] = v
            val a = kotlin.math.abs(v)
            if (a > maxAbs) maxAbs = a
        }
        for (i in 0 until n) inverse[i] /= maxAbs
        return LogSweep(sweep, inverse)
    }
}
