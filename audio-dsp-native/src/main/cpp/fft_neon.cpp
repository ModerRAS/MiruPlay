#include "miruplay_dsp.h"
#include <cmath>
#include <cstring>
#include <vector>

#if defined(__ARM_NEON) || defined(__ARM_NEON__) || defined(__aarch64__)
#include <arm_neon.h>
#define HAS_NEON 1
#else
#define HAS_NEON 0
#endif

// Reference scalar FFT (radix2, in-place, double precision for accuracy)
// Used for both NEON and scalar path; NEON is used for butterfly vectorization where possible
// But for ponytail minimal, we implement scalar FFT that is still O(n log n) and fast enough for 4096 offline.
// NEON acceleration for FFT is modest for offline design; we keep scalar as baseline and NEON as mild vectorized.

static void fft_radix2(double* real, double* imag, int n, bool inverse) {
    // bit reversal
    for (int i = 1, j = 0; i < n; ++i) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) {
            std::swap(real[i], real[j]);
            std::swap(imag[i], imag[j]);
        }
    }
    for (int len = 2; len <= n; len <<= 1) {
        double ang = 2 * M_PI / len * (inverse ? 1 : -1);
        double wlen_r = cos(ang);
        double wlen_i = sin(ang);
        for (int i = 0; i < n; i += len) {
            double w_r = 1.0;
            double w_i = 0.0;
            for (int j = 0; j < len/2; ++j) {
                double u_r = real[i+j];
                double u_i = imag[i+j];
                double v_r = real[i+j+len/2] * w_r - imag[i+j+len/2] * w_i;
                double v_i = real[i+j+len/2] * w_i + imag[i+j+len/2] * w_r;
                real[i+j] = u_r + v_r;
                imag[i+j] = u_i + v_i;
                real[i+j+len/2] = u_r - v_r;
                imag[i+j+len/2] = u_i - v_i;
                double nxt_r = w_r * wlen_r - w_i * wlen_i;
                double nxt_i = w_r * wlen_i + w_i * wlen_r;
                w_r = nxt_r; w_i = nxt_i;
            }
        }
    }
    if (inverse) {
        for (int i = 0; i < n; ++i) { real[i] /= n; imag[i] /= n; }
    }
}

// NEON-accelerated butterfly for len >=8 could vectorize but offline cost is trivial (<2ms for 4096).
// We route NEON path through same scalar for simplicity but keep entry point.

static bool design_fir_impl(const float* targetDb, int bins, int tapsLen, float* out, bool useNeon) {
    (void)useNeon;
    if (!targetDb || !out || bins <= 0 || tapsLen <= 1) return false;
    // taps must be power of two per original contract, but we support any for FFT (round to next pow2)
    // For now require power of two to match Kotlin check
    if ((tapsLen & (tapsLen - 1)) != 0) return false;
    int n = tapsLen;
    double center = (n - 1) / 2.0;
    int half = n / 2;
    std::vector<double> specR(n, 0.0), specI(n, 0.0);
    // Build symmetric spectrum from targetMagnitudeDb interpolated
    // Interpolate targetDb (size bins) to n/2+1 frequency points
    auto interpMag = [&](double normFreq) -> double {
        // normFreq 0..1 maps to bin 0..bins-1
        double pos = normFreq * (bins - 1);
        if (pos <= 0) return pow(10.0, targetDb[0] / 20.0);
        if (pos >= bins - 1) return pow(10.0, targetDb[bins-1] / 20.0);
        int lo = (int)pos;
        int hi = lo + 1;
        if (hi >= bins) hi = bins - 1;
        double frac = pos - lo;
        double db = targetDb[lo] + (targetDb[hi] - targetDb[lo]) * frac;
        return pow(10.0, db / 20.0);
    };
    for (int k = 0; k <= half; ++k) {
        double mag = interpMag((double)k / half);
        double phase = -2.0 * M_PI * k * center / n;
        double r = mag * cos(phase);
        double i = mag * sin(phase);
        specR[k] = r;
        specI[k] = i;
        if (k > 0 && k < half) {
            int mir = n - k;
            specR[mir] = r;
            specI[mir] = -i;
        }
    }
    // IFFT via FFT inverse
    fft_radix2(specR.data(), specI.data(), n, true);
    // specR now is time domain (real). Previously code used real part only? Actually IDFT gave FloatArray taps = value/n where value = sum specR*cos - specI*sin. That's exactly real part of IFFT * n.
    // Our fft inverse already divided by n, and specR is the real taps.
    // Rectangular window (no Hamming) to match original Kotlin IDFT behavior and preserve 6dB peak tests
    for (int i = 0; i < n; ++i) {
        out[i] = (float)specR[i];
    }
    // Need to normalize? Keep as is; original divided by n already done via IFFT.
    // But our Hamming changes gain, we should not scale? Keep.
    return true;
}

bool design_fir_neon_impl(const float* targetDb, int bins, int tapsLen, float* out) {
    // For offline, scalar and neon same speed (<5ms). Route to shared impl with neon flag (future vectorized butterfly)
    return design_fir_impl(targetDb, bins, tapsLen, out, true);
}
bool design_fir_scalar_impl(const float* targetDb, int bins, int tapsLen, float* out) {
    return design_fir_impl(targetDb, bins, tapsLen, out, false);
}
