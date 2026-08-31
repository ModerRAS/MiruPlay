#include "miruplay_dsp.h"
#include <android/log.h>
#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <algorithm>

#define LOG_TAG "MiruDsp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward from fir_neon.cpp / fft_neon.cpp
extern void fir_process_batch_neon_impl(FirContext* ctx, const float* in, float* out, int frames);
extern void fir_process_batch_scalar_impl(FirContext* ctx, const float* in, float* out, int frames);
extern bool design_fir_neon_impl(const float* targetDb, int bins, int tapsLen, float* out);
extern bool design_fir_scalar_impl(const float* targetDb, int bins, int tapsLen, float* out);

static bool g_neonChecked = false;
static bool g_neonSupported = false;

bool cpu_supports_neon() {
    if (g_neonChecked) return g_neonSupported;
    g_neonChecked = true;
#if defined(__aarch64__)
    g_neonSupported = true;
#elif defined(__ARM_NEON) || defined(__ARM_NEON__)
    // Compiled with -mfpu=neon, assume available on minSdk 28 devices (practically all)
    g_neonSupported = true;
#else
    g_neonSupported = false;
#endif
    return g_neonSupported;
}

static void* aligned_alloc16(size_t size) {
    void* ptr = nullptr;
    // posix_memalign requires alignment power of two
    if (posix_memalign(&ptr, 16, size) != 0) return nullptr;
    return ptr;
}

FirContext* fir_context_create(int channels, int tapsLen, const float* const* tapsByChannel,
                               double preamp, const double* channelGain) {
    if (channels <= 0 || channels > 8) return nullptr;
    if (tapsLen <= 0 || tapsLen > 8192) return nullptr;
    FirContext* ctx = new (std::nothrow) FirContext();
    if (!ctx) return nullptr;
    ctx->channels = channels;
    ctx->tapsLen = tapsLen;
    ctx->cursor = 0;
    ctx->useDouble = (tapsLen < 4096);
    ctx->preamp = preamp;
    ctx->preampF = (float)preamp;

    if (ctx->useDouble) {
    size_t tapsBytes = (size_t)channels * tapsLen * sizeof(double);
    size_t historyBytes = (size_t)channels * tapsLen * 2 * sizeof(double);
    size_t gainBytes = (size_t)channels * sizeof(double);
    ctx->tapsAligned = (double*)aligned_alloc16(tapsBytes);
    ctx->history = (double*)aligned_alloc16(historyBytes);
    ctx->channelGain = (double*)aligned_alloc16(gainBytes);
    if (!ctx->tapsAligned || !ctx->history || !ctx->channelGain) { fir_context_destroy(ctx); return nullptr; }
    memset(ctx->history, 0, historyBytes);
    } else {
    size_t tapsBytes = (size_t)channels * tapsLen * sizeof(float);
    size_t historyBytes = (size_t)channels * tapsLen * 2 * sizeof(float);
    size_t gainBytes = (size_t)channels * sizeof(float);
    ctx->tapsAlignedF = (float*)aligned_alloc16(tapsBytes);
    ctx->historyF = (float*)aligned_alloc16(historyBytes);
    ctx->channelGainF = (float*)aligned_alloc16(gainBytes);
    if (!ctx->tapsAlignedF || !ctx->historyF || !ctx->channelGainF) { fir_context_destroy(ctx); return nullptr; }
    memset(ctx->historyF, 0, historyBytes);
    }

    if (ctx->useDouble) {
    for (int ch = 0; ch < channels; ++ch) {
        const float* src = tapsByChannel[ch];
        double* dst = ctx->tapsAligned + ch * tapsLen;
        if (src) for (int i=0;i<tapsLen;++i) dst[i]=(double)src[tapsLen-1-i];
        else memset(dst,0,tapsLen*sizeof(double));
    }
    if (channelGain) memcpy(ctx->channelGain, channelGain, (size_t)channels*sizeof(double));
    else for (int i=0;i<channels;++i) ctx->channelGain[i]=1.0;
    } else {
    for (int ch = 0; ch < channels; ++ch) {
        const float* src = tapsByChannel[ch];
        float* dst = ctx->tapsAlignedF + ch * tapsLen;
        if (src) for (int i=0;i<tapsLen;++i) dst[i]=src[tapsLen-1-i];
        else memset(dst,0,tapsLen*sizeof(float));
    }
    if (channelGain) for (int i=0;i<channels;++i) ctx->channelGainF[i]=(float)channelGain[i];
    else for (int i=0;i<channels;++i) ctx->channelGainF[i]=1.0f;
    }
    return ctx;
}

void fir_context_destroy(FirContext* ctx) {
    if (!ctx) return;
    if (ctx->tapsAligned) free(ctx->tapsAligned);
    if (ctx->history) free(ctx->history);
    if (ctx->channelGain) free(ctx->channelGain);
    if (ctx->tapsAlignedF) free(ctx->tapsAlignedF);
    if (ctx->historyF) free(ctx->historyF);
    if (ctx->channelGainF) free(ctx->channelGainF);
    delete ctx;
}

void fir_context_reset(FirContext* ctx) {
    if (!ctx) return;
    ctx->cursor = 0;
    if (ctx->useDouble) {
        if (ctx->history) { size_t b=(size_t)ctx->channels*ctx->tapsLen*2*sizeof(double); memset(ctx->history,0,b); }
    } else {
        if (ctx->historyF) { size_t b=(size_t)ctx->channels*ctx->tapsLen*2*sizeof(float); memset(ctx->historyF,0,b); }
    }
}

void fir_process_batch_neon(FirContext* ctx, const float* in, float* out, int frames) {
    if (cpu_supports_neon()) {
        fir_process_batch_neon_impl(ctx, in, out, frames);
    } else {
        fir_process_batch_scalar_impl(ctx, in, out, frames);
    }
}

void fir_process_batch_scalar(FirContext* ctx, const float* in, float* out, int frames) {
    fir_process_batch_scalar_impl(ctx, in, out, frames);
}

bool design_fir_neon(const float* targetDb, int bins, int tapsLen, float* outTaps) {
    if (cpu_supports_neon()) {
        return design_fir_neon_impl(targetDb, bins, tapsLen, outTaps);
    } else {
        return design_fir_scalar_impl(targetDb, bins, tapsLen, outTaps);
    }
}

bool design_fir_scalar(const float* targetDb, int bins, int tapsLen, float* outTaps) {
    return design_fir_scalar_impl(targetDb, bins, tapsLen, outTaps);
}

// ---------------- JNI ----------------
extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    bool neon = cpu_supports_neon();
    LOGI("MiruDsp JNI_OnLoad abi=%s neon=%d tapsMax=4096",
#if defined(__aarch64__)
         "arm64-v8a",
#elif defined(__arm__)
         "armeabi-v7a",
#else
         "unknown",
#endif
         neon ? 1 : 0);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* /*vm*/, void* /*reserved*/) {
    LOGI("MiruDsp JNI_OnUnload");
}

// com.miruplay.tv.audio.NativeDspBridge
JNIEXPORT jboolean JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeIsNeonAvailable(JNIEnv* /*env*/, jclass /*clazz*/) {
    return cpu_supports_neon() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeIsAvailable(JNIEnv* /*env*/, jclass /*clazz*/) {
    // Library loaded => available
    return JNI_TRUE;
}

// Create: int channels, int tapsLen, float[][] tapsByChannel, float preamp, float[] channelGain
JNIEXPORT jlong JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeCreate(JNIEnv* env, jclass /*clazz*/,
                                                         jint channels, jint tapsLen,
                                                         jobjectArray tapsByChannel,
                                                         jfloat preamp, jfloatArray channelGainArray) {
    if (channels <= 0 || tapsLen <= 0) return 0;
    const float* tmpTaps[8] = {nullptr};
    float* ownedCopies[8] = {nullptr};

    // Extract taps per channel (copy to temp)
    for (int ch = 0; ch < channels && ch < 8; ++ch) {
        jobject obj = env->GetObjectArrayElement(tapsByChannel, ch);
        if (obj) {
            jfloatArray arr = (jfloatArray)obj;
            jsize len = env->GetArrayLength(arr);
            if (len != tapsLen) {
                LOGE("nativeCreate tapsLen mismatch ch=%d len=%d expected=%d", ch, (int)len, (int)tapsLen);
                env->DeleteLocalRef(obj);
                for (int k = 0; k < 8; ++k) if (ownedCopies[k]) free(ownedCopies[k]);
                return 0;
            }
            jfloat* elems = env->GetFloatArrayElements(arr, nullptr);
            float* copy = (float*)malloc(tapsLen * sizeof(float));
            memcpy(copy, elems, tapsLen * sizeof(float));
            env->ReleaseFloatArrayElements(arr, elems, JNI_ABORT);
            ownedCopies[ch] = copy;
            tmpTaps[ch] = copy;
        } else {
            tmpTaps[ch] = nullptr;
        }
        env->DeleteLocalRef(obj);
    }

    double gainTmp[8] = {1,1,1,1,1,1,1,1};
    if (channelGainArray) {
        jsize glen = env->GetArrayLength(channelGainArray);
        jfloat* gElems = env->GetFloatArrayElements(channelGainArray, nullptr);
        for (int i = 0; i < channels && i < glen; ++i) gainTmp[i] = (double)gElems[i];
        env->ReleaseFloatArrayElements(channelGainArray, gElems, JNI_ABORT);
    }

    FirContext* ctx = fir_context_create(channels, tapsLen, tmpTaps, preamp, gainTmp);

    for (int k = 0; k < 8; ++k) if (ownedCopies[k]) free(ownedCopies[k]);

    if (!ctx) {
        LOGE("nativeCreate failed channels=%d tapsLen=%d", (int)channels, (int)tapsLen);
        return 0;
    }
    LOGI("nativeCreate handle=%p channels=%d taps=%d preamp=%f", ctx, (int)channels, (int)tapsLen, preamp);
    return (jlong)(intptr_t)ctx;
}

JNIEXPORT void JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeRelease(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    FirContext* ctx = (FirContext*)(intptr_t)handle;
    if (!ctx) return;
    LOGI("nativeRelease handle=%p", ctx);
    fir_context_destroy(ctx);
}

JNIEXPORT void JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeReset(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    FirContext* ctx = (FirContext*)(intptr_t)handle;
    if (!ctx) return;
    fir_context_reset(ctx);
}

// Process using direct ByteBuffers (FloatBuffer). Avoid array copy.
JNIEXPORT void JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeProcessDirect(JNIEnv* env, jclass /*clazz*/,
                                                               jlong handle,
                                                               jobject inBuffer, jobject outBuffer,
                                                               jint frames) {
    FirContext* ctx = (FirContext*)(intptr_t)handle;
    if (!ctx || !inBuffer || !outBuffer || frames <= 0) return;
    float* inPtr = (float*)env->GetDirectBufferAddress(inBuffer);
    float* outPtr = (float*)env->GetDirectBufferAddress(outBuffer);
    if (!inPtr || !outPtr) {
        LOGE("nativeProcessDirect GetDirectBufferAddress null");
        return;
    }
    // Ensure capacity not needed: caller guarantees frames*channels floats
    fir_process_batch_neon(ctx, inPtr, outPtr, frames);
}

// Fallback array path (for tests without direct buffers)
JNIEXPORT void JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeProcessArray(JNIEnv* env, jclass /*clazz*/,
                                                              jlong handle,
                                                              jfloatArray inArray, jint inOffset,
                                                              jfloatArray outArray, jint outOffset,
                                                              jint frames) {
    FirContext* ctx = (FirContext*)(intptr_t)handle;
    if (!ctx || !inArray || !outArray || frames <= 0) return;
    jfloat* inPtr = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(inArray, nullptr));
    jfloat* outPtr = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(outArray, nullptr));
    if (!inPtr || !outPtr) {
        if (inPtr) env->ReleasePrimitiveArrayCritical(inArray, inPtr, JNI_ABORT);
        if (outPtr) env->ReleasePrimitiveArrayCritical(outArray, outPtr, JNI_ABORT);
        return;
    }
    fir_process_batch_neon(ctx, inPtr + inOffset, outPtr + outOffset, frames);
    env->ReleasePrimitiveArrayCritical(inArray, inPtr, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(outArray, outPtr, 0);
}

// Update plan without rebuilding history if tapsLen/channels same (fast path)
JNIEXPORT jlong JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeUpdateTaps(JNIEnv* env, jclass /*clazz*/,
                                                             jlong handle,
                                                             jobjectArray tapsByChannel,
                                                             jfloat preamp, jfloatArray channelGainArray) {
    FirContext* ctx = (FirContext*)(intptr_t)handle;
    if (!ctx) return 0;
    int channels = ctx->channels;
    int tapsLen = ctx->tapsLen;
    // update taps in place (reversed)
    if (ctx->useDouble) {
    for (int ch = 0; ch < channels; ++ch) {
        jobject obj = env->GetObjectArrayElement(tapsByChannel, ch);
        if (!obj) continue;
        jfloatArray arr = (jfloatArray)obj;
        jfloat* elems = env->GetFloatArrayElements(arr, nullptr);
        double* dst = ctx->tapsAligned + ch * tapsLen;
        for (int i = 0; i < tapsLen; ++i) dst[i] = (double)elems[tapsLen - 1 - i];
        env->ReleaseFloatArrayElements(arr, elems, JNI_ABORT);
        env->DeleteLocalRef(obj);
    }
    ctx->preamp = (double)preamp;
    if (channelGainArray) {
        jfloat* gElems = env->GetFloatArrayElements(channelGainArray, nullptr);
        jsize glen = env->GetArrayLength(channelGainArray);
        for (int i = 0; i < channels && i < glen; ++i) ctx->channelGain[i] = (double)gElems[i];
        env->ReleaseFloatArrayElements(channelGainArray, gElems, JNI_ABORT);
    }
    } else {
    for (int ch = 0; ch < channels; ++ch) {
        jobject obj = env->GetObjectArrayElement(tapsByChannel, ch);
        if (!obj) continue;
        jfloatArray arr = (jfloatArray)obj;
        jfloat* elems = env->GetFloatArrayElements(arr, nullptr);
        float* dst = ctx->tapsAlignedF + ch * tapsLen;
        for (int i = 0; i < tapsLen; ++i) dst[i] = elems[tapsLen - 1 - i];
        env->ReleaseFloatArrayElements(arr, elems, JNI_ABORT);
        env->DeleteLocalRef(obj);
    }
    ctx->preampF = preamp;
    if (channelGainArray) {
        jfloat* gElems = env->GetFloatArrayElements(channelGainArray, nullptr);
        jsize glen = env->GetArrayLength(channelGainArray);
        for (int i = 0; i < channels && i < glen; ++i) ctx->channelGainF[i] = gElems[i];
        env->ReleaseFloatArrayElements(channelGainArray, gElems, JNI_ABORT);
    }
    }
    // keep history/cursor (crossfade handles via Kotlin layer)
    return handle;
}

// Design FIR: float[] targetDb, int sampleRate (unused, kept for API parity), int taps -> float[] out
JNIEXPORT jfloatArray JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeDesignFir(JNIEnv* env, jclass /*clazz*/,
                                                            jfloatArray targetDbArray,
                                                            jint taps) {
    if (!targetDbArray || taps <= 0) return nullptr;
    jsize bins = env->GetArrayLength(targetDbArray);
    jfloat* target = env->GetFloatArrayElements(targetDbArray, nullptr);
    jfloatArray out = env->NewFloatArray(taps);
    jfloat* outElems = env->GetFloatArrayElements(out, nullptr);

    // Convert float targetDb to float* (already)
    bool ok = design_fir_neon((float*)target, bins, taps, (float*)outElems);
    env->ReleaseFloatArrayElements(targetDbArray, target, JNI_ABORT);
    env->ReleaseFloatArrayElements(out, outElems, ok ? 0 : JNI_ABORT);
    if (!ok) {
        // fallback scalar already inside design_fir_neon, but if still fail return null?
        return out;
    }
    return out;
}

JNIEXPORT jfloatArray JNICALL
Java_com_miruplay_tv_audio_NativeDspBridge_nativeDesignFirScalar(JNIEnv* env, jclass /*clazz*/,
                                                                  jfloatArray targetDbArray,
                                                                  jint taps) {
    if (!targetDbArray || taps <= 0) return nullptr;
    jsize bins = env->GetArrayLength(targetDbArray);
    jfloat* target = env->GetFloatArrayElements(targetDbArray, nullptr);
    jfloatArray out = env->NewFloatArray(taps);
    jfloat* outElems = env->GetFloatArrayElements(out, nullptr);
    bool ok = design_fir_scalar((float*)target, bins, taps, (float*)outElems);
    env->ReleaseFloatArrayElements(targetDbArray, target, JNI_ABORT);
    env->ReleaseFloatArrayElements(out, outElems, ok ? 0 : JNI_ABORT);
    (void)ok;
    return out;
}

} // extern C
