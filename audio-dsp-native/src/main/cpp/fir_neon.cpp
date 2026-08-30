#include "miruplay_dsp.h"
#include <cstring>
#include <algorithm>

#if defined(__ARM_NEON) || defined(__ARM_NEON__) || defined(__aarch64__)
#include <arm_neon.h>
#define HAS_NEON 1
#else
#define HAS_NEON 0
#endif

static inline double dot_scalar(const double* a, const double* b, int len) {
    double sum = 0.0;
    for (int i = 0; i < len; ++i) sum += a[i] * b[i];
    return sum;
}

#if HAS_NEON
#if defined(__aarch64__)
// ponytail: double precision 64-bit, 2 per vector (128/64=2), so half throughput vs float32 4x but user requested double
static inline double dot_neon(const double* a, const double* b, int len) {
    int i = 0;
    float64x2_t acc = vdupq_n_f64(0.0);
    int vecLen = len & ~1; // multiples of 2
    for (; i < vecLen; i += 2) {
        float64x2_t av = vld1q_f64(a + i);
        float64x2_t bv = vld1q_f64(b + i);
        acc = vfmaq_f64(acc, av, bv);
    }
    double sum = vaddvq_f64(acc);
    for (; i < len; ++i) sum += a[i] * b[i];
    return sum;
}
#else
// arm32: NEON has no double, fallback to scalar double (VFP)
static inline double dot_neon_arm32(const double* a, const double* b, int len) {
    // ponytail: global fallback, no NEON f64 on arm32
    double sum = 0.0;
    for (int i = 0; i < len; ++i) sum += a[i] * b[i];
    return sum;
}
#endif
#endif

void fir_process_batch_scalar_impl(FirContext* ctx, const float* in, float* out, int frames) {
    if (!ctx || !in || !out || frames <= 0) return;
    int C = ctx->channels;
    int T = ctx->tapsLen;
    if (T == 0) {
        for (int f = 0; f < frames; ++f) {
            for (int c = 0; c < C; ++c) {
                double v = (double)in[f * C + c];
                v *= ctx->preamp * ctx->channelGain[c];
                out[f * C + c] = (float)v;
            }
        }
        return;
    }
    for (int f = 0; f < frames; ++f) {
        for (int ch = 0; ch < C; ++ch) {
            double inSample = (double)in[f * C + ch];
            double* histBase = ctx->history + ch * (T * 2);
            histBase[ctx->cursor] = inSample;
            histBase[ctx->cursor + T] = inSample;
            const double* taps = ctx->tapsAligned + ch * T;
            const double* win = histBase + ctx->cursor + 1;
            double filtered = dot_scalar(taps, win, T);
            double v = filtered * ctx->preamp * ctx->channelGain[ch];
            out[f * C + ch] = (float)v;
        }
        ctx->cursor++;
        if (ctx->cursor >= T) ctx->cursor = 0;
    }
}

void fir_process_batch_neon_impl(FirContext* ctx, const float* in, float* out, int frames) {
    if (!ctx || !in || !out || frames <= 0) return;
    int C = ctx->channels;
    int T = ctx->tapsLen;
#if HAS_NEON
    if (T == 0) {
        for (int f = 0; f < frames; ++f) {
            for (int c = 0; c < C; ++c) {
                double v = (double)in[f * C + c];
                v *= ctx->preamp * ctx->channelGain[c];
                out[f * C + c] = (float)v;
            }
        }
        return;
    }
    for (int f = 0; f < frames; ++f) {
        for (int ch = 0; ch < C; ++ch) {
            double inSample = (double)in[f * C + ch];
            double* histBase = ctx->history + ch * (T * 2);
            histBase[ctx->cursor] = inSample;
            histBase[ctx->cursor + T] = inSample;
            const double* taps = ctx->tapsAligned + ch * T;
            const double* win = histBase + ctx->cursor + 1;
            double filtered;
#if defined(__aarch64__)
            filtered = dot_neon(taps, win, T);
#else
            filtered = dot_neon_arm32(taps, win, T);
#endif
            double v = filtered * ctx->preamp * ctx->channelGain[ch];
            out[f * C + ch] = (float)v;
        }
        ctx->cursor++;
        if (ctx->cursor >= T) ctx->cursor = 0;
    }
#else
    fir_process_batch_scalar_impl(ctx, in, out, frames);
#endif
}
