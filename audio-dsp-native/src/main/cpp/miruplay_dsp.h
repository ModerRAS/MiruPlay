#pragma once
#include <cstdint>
#include <cstddef>

struct FirContext {
    int channels = 0;
    int tapsLen = 0;
    int cursor = 0; // 0..tapsLen-1 shared
    // per-channel aligned buffers - double precision (user requested 64-bit)
    double* tapsAligned = nullptr;      // [channels * tapsLen] reversed for linear dot
    double* history = nullptr;          // [channels * tapsLen * 2] mirror
    double* channelGain = nullptr;      // [channels]
    double preamp = 1.0;
    // biquad state if needed later (kept for arena completeness)
    bool hasBiquads = false;
};

// Lifecycle: one alloc at create, zero alloc in process
FirContext* fir_context_create(int channels, int tapsLen, const float* const* tapsByChannel,
                               double preamp, const double* channelGain);
void fir_context_destroy(FirContext* ctx);
void fir_context_reset(FirContext* ctx); // zero history, cursor=0

// Batch process: in/out are interleaved FloatBuffer direct pointers, frames = number of frames
// Zero malloc, NEON inside
void fir_process_batch_neon(FirContext* ctx,
                            const float* inInterleaved,
                            float* outInterleaved,
                            int frames);

// Scalar fallback (no NEON)
void fir_process_batch_scalar(FirContext* ctx,
                              const float* inInterleaved,
                              float* outInterleaved,
                              int frames);

bool cpu_supports_neon();

// FFT design (offline, not on audio thread)
bool design_fir_neon(const float* targetMagnitudeDb, int bins, int tapsLen, float* outTaps);
bool design_fir_scalar(const float* targetMagnitudeDb, int bins, int tapsLen, float* outTaps);
