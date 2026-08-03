package com.miruplay.tv.audio

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspFilterType
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    fun magnitudeAt(frequencyHz: Double, sampleRateHz: Double): Double {
        val omega = 2.0 * Math.PI * frequencyHz / sampleRateHz
        val cosW = cos(omega)
        val sinW = sin(omega)
        val nReal = b0 + b1 * cosW + b2 * cos(2.0 * omega)
        val nImag = -b1 * sinW - b2 * sin(2.0 * omega)
        val dReal = 1.0 + a1 * cosW + a2 * cos(2.0 * omega)
        val dImag = -a1 * sinW - a2 * sin(2.0 * omega)
        return sqrt((nReal * nReal + nImag * nImag) / (dReal * dReal + dImag * dImag))
    }
}

object BiquadDesigner {
    fun design(band: AudioDspBand, sampleRateHz: Int): BiquadCoefficients {
        val normalized = band.normalized()
        val omega = 2.0 * Math.PI * normalized.frequencyHz / sampleRateHz
        val alpha = sin(omega) / (2.0 * normalized.q)
        val cosW = cos(omega)
        val gain = 10.0.pow(normalized.gainDb / 40.0)
        val beta = 2.0 * sqrt(gain) * alpha
        val raw = when (normalized.type) {
            AudioDspFilterType.PEAKING -> doubleArrayOf(
                1.0 + alpha * gain, -2.0 * cosW, 1.0 - alpha * gain,
                1.0 + alpha / gain, -2.0 * cosW, 1.0 - alpha / gain,
            )
            AudioDspFilterType.LOW_SHELF -> doubleArrayOf(
                gain * ((gain + 1.0) - (gain - 1.0) * cosW + beta),
                2.0 * gain * ((gain - 1.0) - (gain + 1.0) * cosW),
                gain * ((gain + 1.0) - (gain - 1.0) * cosW - beta),
                (gain + 1.0) + (gain - 1.0) * cosW + beta,
                -2.0 * ((gain - 1.0) + (gain + 1.0) * cosW),
                (gain + 1.0) + (gain - 1.0) * cosW - beta,
            )
            AudioDspFilterType.HIGH_SHELF -> doubleArrayOf(
                gain * ((gain + 1.0) + (gain - 1.0) * cosW + beta),
                -2.0 * gain * ((gain - 1.0) + (gain + 1.0) * cosW),
                gain * ((gain + 1.0) + (gain - 1.0) * cosW - beta),
                (gain + 1.0) - (gain - 1.0) * cosW + beta,
                2.0 * ((gain - 1.0) - (gain + 1.0) * cosW),
                (gain + 1.0) - (gain - 1.0) * cosW - beta,
            )
            AudioDspFilterType.LOW_PASS -> doubleArrayOf(
                (1.0 - cosW) / 2.0, 1.0 - cosW, (1.0 - cosW) / 2.0,
                1.0 + alpha, -2.0 * cosW, 1.0 - alpha,
            )
            AudioDspFilterType.HIGH_PASS -> doubleArrayOf(
                (1.0 + cosW) / 2.0, -(1.0 + cosW), (1.0 + cosW) / 2.0,
                1.0 + alpha, -2.0 * cosW, 1.0 - alpha,
            )
            AudioDspFilterType.NOTCH -> doubleArrayOf(
                1.0, -2.0 * cosW, 1.0,
                1.0 + alpha, -2.0 * cosW, 1.0 - alpha,
            )
            AudioDspFilterType.BAND_PASS -> doubleArrayOf(
                alpha, 0.0, -alpha,
                1.0 + alpha, -2.0 * cosW, 1.0 - alpha,
            )
        }
        val a0 = raw[3]
        return BiquadCoefficients(raw[0] / a0, raw[1] / a0, raw[2] / a0, raw[4] / a0, raw[5] / a0)
    }
}
