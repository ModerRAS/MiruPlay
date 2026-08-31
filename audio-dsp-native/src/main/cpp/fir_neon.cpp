#include "miruplay_dsp.h"
#include <cstring>
#include <algorithm>

#if defined(__ARM_NEON) || defined(__ARM_NEON__) || defined(__aarch64__)
#include <arm_neon.h>
#define HAS_NEON 1
#else
#define HAS_NEON 0
#endif

static inline float dot_scalar_f(const float* a, const float* b, int len) { float s=0; for(int i=0;i<len;++i) s+=a[i]*b[i]; return s; }
static inline double dot_scalar_d(const double* a, const double* b, int len) { double s=0; for(int i=0;i<len;++i) s+=a[i]*b[i]; return s; }

#if HAS_NEON
#if defined(__aarch64__)
static inline float dot_neon_f(const float* a, const float* b, int len) {
    int i=0; float32x4_t acc=vdupq_n_f32(0); int v=len&~3;
    for(;i<v;i+=4){ acc=vmlaq_f32(acc, vld1q_f32(a+i), vld1q_f32(b+i)); }
    float s=vaddvq_f32(acc); for(;i<len;++i) s+=a[i]*b[i]; return s;
}
static inline double dot_neon_d(const double* a, const double* b, int len) {
    int i=0; float64x2_t acc=vdupq_n_f64(0); int v=len&~1;
    for(;i<v;i+=2){ acc=vfmaq_f64(acc, vld1q_f64(a+i), vld1q_f64(b+i)); }
    double s=vaddvq_f64(acc); for(;i<len;++i) s+=a[i]*b[i]; return s;
}
#else
static inline float dot_neon_f_arm32(const float* a, const float* b, int len) {
    int i=0; float32x4_t acc=vdupq_n_f32(0); int v=len&~3;
    for(;i<v;i+=4){ acc=vmlaq_f32(acc, vld1q_f32(a+i), vld1q_f32(b+i)); }
    float32x2_t s2=vadd_f32(vget_low_f32(acc), vget_high_f32(acc)); s2=vpadd_f32(s2,s2);
    float s=vget_lane_f32(s2,0); for(;i<len;++i) s+=a[i]*b[i]; return s;
}
static inline double dot_neon_d_arm32(const double* a, const double* b, int len) {
    double s=0; for(int i=0;i<len;++i) s+=a[i]*b[i]; return s;
}
#endif
#endif

void fir_process_batch_scalar_impl(FirContext* ctx, const float* in, float* out, int frames) {
    if (!ctx||!in||!out||frames<=0) return;
    int C=ctx->channels; int T=ctx->tapsLen;
    if (T==0) { for(int f=0;f<frames;++f) for(int c=0;c<C;++c){ double v=in[f*C+c]; v*=ctx->preamp*ctx->channelGain[c]; if(!ctx->useDouble) v=in[f*C+c]*ctx->preampF*ctx->channelGainF[c]; out[f*C+c]=(float)v; } return; }
    if (ctx->useDouble) {
        for(int f=0;f<frames;++f){ for(int ch=0;ch<C;++ch){ double s=in[f*C+ch]; double* hb=ctx->history+ch*(T*2); hb[ctx->cursor]=s; hb[ctx->cursor+T]=s; const double* taps=ctx->tapsAligned+ch*T; const double* win=hb+ctx->cursor+1; double fil=dot_scalar_d(taps,win,T); double v=fil*ctx->preamp*ctx->channelGain[ch]; out[f*C+ch]=(float)v; } ctx->cursor++; if(ctx->cursor>=T) ctx->cursor=0; }
    } else {
        for(int f=0;f<frames;++f){ for(int ch=0;ch<C;++ch){ float s=in[f*C+ch]; float* hb=ctx->historyF+ch*(T*2); hb[ctx->cursor]=s; hb[ctx->cursor+T]=s; const float* taps=ctx->tapsAlignedF+ch*T; const float* win=hb+ctx->cursor+1; float fil=dot_scalar_f(taps,win,T); float v=fil*ctx->preampF*ctx->channelGainF[ch]; out[f*C+ch]=v; } ctx->cursor++; if(ctx->cursor>=T) ctx->cursor=0; }
    }
}

void fir_process_batch_neon_impl(FirContext* ctx, const float* in, float* out, int frames) {
    if (!ctx||!in||!out||frames<=0) return;
    int C=ctx->channels; int T=ctx->tapsLen;
#if HAS_NEON
    if (T==0) { for(int f=0;f<frames;++f) for(int c=0;c<C;++c){ double v=in[f*C+c]; if(ctx->useDouble) v*=ctx->preamp*ctx->channelGain[c]; else v*=ctx->preampF*ctx->channelGainF[c]; out[f*C+c]=(float)v; } return; }
    if (ctx->useDouble) {
        for(int f=0;f<frames;++f){ for(int ch=0;ch<C;++ch){ double s=in[f*C+ch]; double* hb=ctx->history+ch*(T*2); hb[ctx->cursor]=s; hb[ctx->cursor+T]=s; const double* taps=ctx->tapsAligned+ch*T; const double* win=hb+ctx->cursor+1; double fil;
#if defined(__aarch64__)
        fil=dot_neon_d(taps,win,T);
#else
        fil=dot_neon_d_arm32(taps,win,T);
#endif
        double v=fil*ctx->preamp*ctx->channelGain[ch]; out[f*C+ch]=(float)v; } ctx->cursor++; if(ctx->cursor>=T) ctx->cursor=0; }
    } else {
        for(int f=0;f<frames;++f){ for(int ch=0;ch<C;++ch){ float s=in[f*C+ch]; float* hb=ctx->historyF+ch*(T*2); hb[ctx->cursor]=s; hb[ctx->cursor+T]=s; const float* taps=ctx->tapsAlignedF+ch*T; const float* win=hb+ctx->cursor+1; float fil;
#if defined(__aarch64__)
        fil=dot_neon_f(taps,win,T);
#else
        fil=dot_neon_f_arm32(taps,win,T);
#endif
        float v=fil*ctx->preampF*ctx->channelGainF[ch]; out[f*C+ch]=v; } ctx->cursor++; if(ctx->cursor>=T) ctx->cursor=0; }
    }
#else
    fir_process_batch_scalar_impl(ctx,in,out,frames);
#endif
}
