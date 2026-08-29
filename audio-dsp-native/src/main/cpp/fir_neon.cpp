#include "miruplay_dsp.h"
#include <cstring>
#include <algorithm>

#if defined(__ARM_NEON) || defined(__ARM_NEON__) || defined(__aarch64__)
#include <arm_neon.h>
#define HAS_NEON 1
#else
#define HAS_NEON 0
#endif

static inline float dot_scalar(const float* a, const float* b, int len) {
    float sum = 0.0f;
    for (int i = 0; i < len; ++i) sum += a[i] * b[i];
    return sum;
}

#if HAS_NEON
#if defined(__aarch64__)
static inline float dot_neon(const float* a, const float* b, int len) {
    int i = 0;
    float32x4_t acc = vdupq_n_f32(0.0f);
    int vecLen = len & ~3;
    for (; i < vecLen; i += 4) {
        float32x4_t av = vld1q_f32(a + i);
        float32x4_t bv = vld1q_f32(b + i);
        acc = vmlaq_f32(acc, av, bv);
    }
    float sum = vaddvq_f32(acc);
    for (; i < len; ++i) sum += a[i] * b[i];
    return sum;
}
#else
static inline float dot_neon_arm32(const float* a, const float* b, int len) {
    int i = 0;
    float32x4_t acc = vdupq_n_f32(0.0f);
    int vecLen = len & ~3;
    for (; i < vecLen; i += 4) {
        float32x4_t av = vld1q_f32(a + i);
        float32x4_t bv = vld1q_f32(b + i);
        acc = vmlaq_f32(acc, av, bv);
    }
    float32x2_t sum2 = vadd_f32(vget_low_f32(acc), vget_high_f32(acc));
    sum2 = vpadd_f32(sum2, sum2);
    float sum = vget_lane_f32(sum2, 0);
    for (; i < len; ++i) sum += a[i] * b[i];
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
                float v = in[f * C + c];
                v *= ctx->preamp * ctx->channelGain[c];
                out[f * C + c] = v;
            }
        }
        return;
    }
    for (int f = 0; f < frames; ++f) {
        for (int ch = 0; ch < C; ++ch) {
            float inSample = in[f * C + ch];
            float* histBase = ctx->history + ch * (T * 2);
            histBase[ctx->cursor] = inSample;
            histBase[ctx->cursor + T] = inSample;
            const float* taps = ctx->tapsAligned + ch * T;
            const float* win = histBase + ctx->cursor + 1;
            float filtered = dot_scalar(taps, win, T);
            float v = filtered * ctx->preamp * ctx->channelGain[ch];
            out[f * C + ch] = v;
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
                float v = in[f * C + c];
                v *= ctx->preamp * ctx->channelGain[c];
                out[f * C + c] = v;
            }
        }
        return;
    }
    for (int f = 0; f < frames; ++f) {
        for (int ch = 0; ch < C; ++ch) {
            float inSample = in[f * C + ch];
            float* histBase = ctx->history + ch * (T * 2);
            histBase[ctx->cursor] = inSample;
            histBase[ctx->cursor + T] = inSample;
            const float* taps = ctx->tapsAligned + ch * T;
            const float* win = histBase + ctx->cursor + 1;
            float filtered;
#if defined(__aarch64__)
            filtered = dot_neon(taps, win, T);
#else
            filtered = dot_neon_arm32(taps, win, T);
#endif
            float v = filtered * ctx->preamp * ctx->channelGain[ch];
            out[f * C + ch] = v;
        }
        ctx->cursor++;
        if (ctx->cursor >= T) ctx->cursor = 0;
    }
#else
    fir_process_batch_scalar_impl(ctx, in, out, frames);
#endif
}
