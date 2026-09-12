package com.miruplay.tv.measure

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Minimal radix-2 complex FFT — pure Kotlin, no Android dependency. */
internal object Fft {
    fun nextPow2(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    /** In-place iterative radix-2 complex FFT. [re]/[im] size must be a power of two. */
    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tre = re[i]; re[i] = re[j]; re[j] = tre
                val tim = im[i]; im[i] = im[j]; im[j] = tim
            }
        }
        var len = 2
        while (len <= n) {
            val ang = 2.0 * PI / len * (if (inverse) 1.0 else -1.0)
            val stepRe = cos(ang)
            val stepIm = sin(ang)
            val half = len shr 1
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until half) {
                    val i0 = start + k
                    val i1 = i0 + half
                    val uRe = re[i0]
                    val uIm = im[i0]
                    val vRe = re[i1] * curRe - im[i1] * curIm
                    val vIm = re[i1] * curIm + im[i1] * curRe
                    re[i0] = uRe + vRe
                    im[i0] = uIm + vIm
                    re[i1] = uRe - vRe
                    im[i1] = uIm - vIm
                    val nextRe = curRe * stepRe - curIm * stepIm
                    curIm = curRe * stepIm + curIm * stepRe
                    curRe = nextRe
                }
                start += len
            }
            len = len shl 1
        }
        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }

    /** Real FFT of [x] zero-padded to [n] (power of two). Returns half-spectrum of length n/2+1. */
    fun rfft(x: DoubleArray, n: Int): Pair<DoubleArray, DoubleArray> {
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        System.arraycopy(x, 0, re, 0, x.size)
        transform(re, im, inverse = false)
        val m = n / 2 + 1
        return re.copyOf(m) to im.copyOf(m)
    }

    /** Inverse real FFT from a half-spectrum (length n/2+1) to [n] real samples. */
    fun irfft(hRe: DoubleArray, hIm: DoubleArray, n: Int): DoubleArray {
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val m = n / 2 + 1
        System.arraycopy(hRe, 0, re, 0, m)
        System.arraycopy(hIm, 0, im, 0, m)
        for (i in m until n) {
            val j = n - i
            re[i] = hRe[j]
            im[i] = -hIm[j]
        }
        transform(re, im, inverse = true)
        return re
    }

    /** Linear FFT convolution; output length a.size + b.size - 1. */
    fun fftConvolve(a: DoubleArray, b: DoubleArray): DoubleArray {
        if (a.isEmpty() || b.isEmpty()) return DoubleArray(0)
        val outLen = a.size + b.size - 1
        val n = nextPow2(outLen)
        val m = n / 2 + 1
        val (aRe, aIm) = rfft(a, n)
        val (bRe, bIm) = rfft(b, n)
        val hRe = DoubleArray(m)
        val hIm = DoubleArray(m)
        for (i in 0 until m) {
            hRe[i] = aRe[i] * bRe[i] - aIm[i] * bIm[i]
            hIm[i] = aRe[i] * bIm[i] + aIm[i] * bRe[i]
        }
        return irfft(hRe, hIm, n).copyOf(outLen)
    }
}
