#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {

constexpr const char* kTag = "MiruPlayVlcBridge";
constexpr int kErrInvalidArgs = -1;
constexpr int kErrDlopenFailed = -2;
constexpr int kErrSymbolMissing = -3;
using libvlc_media_player_t = void;
using libvlc_video_lock_cb = void* (*)(void*, void**);
using libvlc_video_unlock_cb = void (*)(void*, void*, void* const*);
using libvlc_video_display_cb = void (*)(void*, void*);
using libvlc_video_format_cb = unsigned (*)(
    void**,
    char*,
    unsigned*,
    unsigned*,
    unsigned*,
    unsigned*
);
using libvlc_video_cleanup_cb = void (*)(void*);
using libvlc_video_set_callbacks_fn = void (*)(
    libvlc_media_player_t*,
    libvlc_video_lock_cb,
    libvlc_video_unlock_cb,
    libvlc_video_display_cb,
    void*
);
using libvlc_video_set_format_callbacks_fn = void (*)(
    libvlc_media_player_t*,
    libvlc_video_format_cb,
    libvlc_video_cleanup_cb
);
using vlc_object_t = void;
using libvlc_video_output_resize_cb = void (*)(void*, unsigned, unsigned);
using libvlc_video_output_mouse_move_cb = void (*)(void*, int, int);
using libvlc_video_output_mouse_press_cb = void (*)(void*, int);
using libvlc_video_output_mouse_release_cb = void (*)(void*, int);
using libvlc_video_output_set_window_cb = void (*)(
    void*,
    libvlc_video_output_resize_cb,
    libvlc_video_output_mouse_move_cb,
    libvlc_video_output_mouse_press_cb,
    libvlc_video_output_mouse_release_cb,
    void*
);

constexpr const char* kDefaultVideoOutputModule = "vmem";
constexpr const char* kDefaultWindowModule = "wextern";
constexpr const char* kDefaultDecoderDevice = "none";
constexpr int VLC_VAR_STRING = 0x0040;
constexpr int VLC_VAR_ADDRESS = 0x0070;

static_assert(VLC_VAR_STRING == 0x0040, "Unexpected VLC_VAR_STRING value");
static_assert(VLC_VAR_ADDRESS == 0x0070, "Unexpected VLC_VAR_ADDRESS value");

union vlc_value_t {
    int64_t i_int;
    bool b_bool;
    float f_float;
    char* psz_string;
    void* p_address;
    struct {
        int32_t x;
        int32_t y;
    } coords;
};

using var_create_fn = int (*)(vlc_object_t*, const char*, int);
using var_set_fn = int (*)(vlc_object_t*, const char*, vlc_value_t);

struct FrameProbeContext {
    std::mutex mutex;
    std::condition_variable callbacksIdle;
    int callbackDepth = 0;
    bool configured = false;
    bool captured = false;
    bool released = false;
    std::string metadataPath;
    std::string previewPath;
    std::string lumaPath;
    std::string rawFramePath;
    std::string sourceChroma;
    std::string preferredChroma;
    std::array<char, 5> chroma{};
    unsigned width = 0;
    unsigned height = 0;
    unsigned visibleWidth = 0;
    unsigned visibleHeight = 0;
    unsigned planeCount = 0;
    std::array<unsigned, 4> pitches{};
    std::array<unsigned, 4> lines{};
    std::array<size_t, 4> offsets{};
    size_t totalBytes = 0;
    uint8_t* buffer = nullptr;
    unsigned lockCalls = 0;
    unsigned unlockCalls = 0;
    unsigned displayCalls = 0;
    unsigned windowWidth = 1;
    unsigned windowHeight = 1;
    libvlc_video_output_resize_cb resizeCallback = nullptr;
    void* resizeOpaque = nullptr;
};

struct FrameProbeSnapshot {
    std::string metadataPath;
    std::string previewPath;
    std::string lumaPath;
    std::string rawFramePath;
    std::string sourceChroma;
    std::string preferredChroma;
    std::string chroma;
    unsigned width = 0;
    unsigned height = 0;
    unsigned visibleWidth = 0;
    unsigned visibleHeight = 0;
    unsigned planeCount = 0;
    std::array<unsigned, 4> pitches{};
    std::array<unsigned, 4> lines{};
    std::array<size_t, 4> offsets{};
    std::vector<uint8_t> buffer;
};

class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv* env, jstring value) : env_(env), value_(value) {
        if (env_ != nullptr && value_ != nullptr) {
            chars_ = env_->GetStringUTFChars(value_, nullptr);
        }
    }

    ~ScopedUtfChars() {
        if (env_ != nullptr && value_ != nullptr && chars_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    const char* get() const {
        return chars_;
    }

    bool valid() const {
        return chars_ != nullptr;
    }

private:
    JNIEnv* env_ = nullptr;
    jstring value_ = nullptr;
    const char* chars_ = nullptr;
};

class CallbackScope {
public:
    explicit CallbackScope(FrameProbeContext* context) : context_(context) {
        if (context_ == nullptr) {
            return;
        }
        std::lock_guard<std::mutex> lock(context_->mutex);
        context_->callbackDepth++;
    }

    ~CallbackScope() {
        if (context_ == nullptr) {
            return;
        }
        std::lock_guard<std::mutex> lock(context_->mutex);
        context_->callbackDepth--;
        context_->callbacksIdle.notify_all();
    }

private:
    FrameProbeContext* context_;
};

void logError(const char* message) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message);
}

void logInfo(const char* message) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", message);
}

void freeProbeBuffer(FrameProbeContext* context) {
    if (context == nullptr || context->buffer == nullptr) {
        return;
    }
    std::free(context->buffer);
    context->buffer = nullptr;
}

std::string readFourcc(const char* chroma) {
    if (chroma == nullptr) {
        return {};
    }
    return std::string(chroma, chroma + 4);
}

bool isRgb32(const std::string& chroma) {
    return chroma == "RV32";
}

bool isPlanar420(const std::string& chroma) {
    return chroma == "I420" || chroma == "YV12" || chroma == "IYUV";
}

bool isHighBitDepthPlanar420(const std::string& chroma) {
    return chroma == "I0AL" || chroma == "I0CL" || chroma == "I0FL" ||
        chroma == "I09L" || chroma == "I09B" || chroma == "I2AL" ||
        chroma == "I2CL" || chroma == "I2FL";
}

bool isSemiPlanar420(const std::string& chroma) {
    return chroma == "NV12" || chroma == "NV21";
}

bool isHighBitDepthSemiPlanar420(const std::string& chroma) {
    return chroma == "P010" || chroma == "P012" || chroma == "P016";
}

bool isSupportedProbeChroma(const std::string& chroma) {
    return isRgb32(chroma) || isPlanar420(chroma) || isHighBitDepthPlanar420(chroma) ||
        isSemiPlanar420(chroma) || isHighBitDepthSemiPlanar420(chroma);
}

std::string selectProbeOutputChroma(
    const FrameProbeContext* context,
    const std::string& sourceChroma
) {
    if (context != nullptr && !context->preferredChroma.empty() &&
        isSupportedProbeChroma(context->preferredChroma)) {
        return context->preferredChroma;
    }
    // Keep the decoder's native chroma whenever possible. On the target box,
    // remapping HDR decoder output such as I0AL to P010 forces libVLC down an
    // unsupported conversion path before VMEM ever receives a frame.
    return sourceChroma;
}

unsigned inferPlaneCount(
    const std::string& chroma,
    const std::array<unsigned, 4>& pitches,
    const std::array<unsigned, 4>& lines
) {
    if (isRgb32(chroma)) {
        return 1;
    }
    if (isSemiPlanar420(chroma) || isHighBitDepthSemiPlanar420(chroma)) {
        return 2;
    }
    if (isPlanar420(chroma) || isHighBitDepthPlanar420(chroma)) {
        return 3;
    }
    unsigned count = 0;
    for (size_t index = 0; index < pitches.size(); ++index) {
        if (pitches[index] > 0 && lines[index] > 0) {
            count++;
        }
    }
    return count;
}

unsigned inferBitDepth(const std::string& chroma) {
    if (isHighBitDepthPlanar420(chroma) || chroma == "P010") {
        return 10;
    }
    if (chroma == "P012") {
        return 12;
    }
    if (chroma == "P016") {
        return 16;
    }
    return 8;
}

unsigned alignUp(unsigned value, unsigned alignment) {
    if (alignment == 0) {
        return value;
    }
    return ((value + alignment - 1u) / alignment) * alignment;
}

bool populateDefaultPlaneLayout(
    const std::string& chroma,
    unsigned width,
    unsigned height,
    std::array<unsigned, 4>* pitches,
    std::array<unsigned, 4>* lines
) {
    if (pitches == nullptr || lines == nullptr || width == 0 || height == 0) {
        return false;
    }
    const unsigned alignedWidth = alignUp(width, 2u);
    const unsigned alignedHeight = alignUp(height, 2u);
    const unsigned chromaWidth = std::max(1u, alignedWidth / 2u);
    const unsigned chromaHeight = std::max(1u, alignedHeight / 2u);
    const unsigned bitDepth = inferBitDepth(chroma);
    const unsigned bytesPerSample = bitDepth > 8 ? 2u : 1u;
    pitches->fill(0u);
    lines->fill(0u);
    if (isRgb32(chroma)) {
        (*pitches)[0] = alignUp(width * 4u, 16u);
        (*lines)[0] = height;
        return true;
    }
    if (isPlanar420(chroma) || isHighBitDepthPlanar420(chroma)) {
        (*pitches)[0] = alignUp(alignedWidth * bytesPerSample, 16u);
        (*pitches)[1] = alignUp(chromaWidth * bytesPerSample, 16u);
        (*pitches)[2] = alignUp(chromaWidth * bytesPerSample, 16u);
        (*lines)[0] = alignedHeight;
        (*lines)[1] = chromaHeight;
        (*lines)[2] = chromaHeight;
        return true;
    }
    if (isSemiPlanar420(chroma)) {
        (*pitches)[0] = alignUp(alignedWidth, 16u);
        (*pitches)[1] = alignUp(alignedWidth, 16u);
        (*lines)[0] = alignedHeight;
        (*lines)[1] = chromaHeight;
        return true;
    }
    if (isHighBitDepthSemiPlanar420(chroma)) {
        (*pitches)[0] = alignUp(alignedWidth * 2u, 16u);
        (*pitches)[1] = alignUp(alignedWidth * 2u, 16u);
        (*lines)[0] = alignedHeight;
        (*lines)[1] = chromaHeight;
        return true;
    }
    return false;
}

bool usesBt2020Matrix(const std::string& chroma) {
    return inferBitDepth(chroma) > 8;
}

uint16_t readSample(
    const uint8_t* plane,
    size_t byteOffset,
    unsigned bitDepth
) {
    if (plane == nullptr) {
        return 0;
    }
    if (bitDepth <= 8) {
        return plane[byteOffset];
    }
    uint16_t rawValue = static_cast<uint16_t>(plane[byteOffset]) |
        static_cast<uint16_t>(plane[byteOffset + 1] << 8);
    const unsigned safeBits = std::min(bitDepth, 16u);
    const unsigned maxCode = safeBits >= 16u ? 65535u : ((1u << safeBits) - 1u);
    if (rawValue > maxCode && bitDepth < 16) {
        rawValue = static_cast<uint16_t>(rawValue >> (16u - bitDepth));
    }
    return static_cast<uint16_t>(std::min<unsigned>(rawValue, maxCode));
}

double clamp01(double value) {
    return std::clamp(value, 0.0, 1.0);
}

double pqEotf(double value) {
    constexpr double m1 = 2610.0 / 16384.0;
    constexpr double m2 = 2523.0 / 32.0;
    constexpr double c1 = 3424.0 / 4096.0;
    constexpr double c2 = 2413.0 / 128.0;
    constexpr double c3 = 2392.0 / 128.0;
    const double power = std::pow(clamp01(value), 1.0 / m2);
    const double numerator = std::max(power - c1, 0.0);
    const double denominator = c2 - c3 * power;
    if (denominator <= 0.0) {
        return 0.0;
    }
    return std::pow(numerator / denominator, 1.0 / m1);
}

std::array<double, 3> bt2020LinearToBt709(
    double rPrime,
    double gPrime,
    double bPrime
) {
    const double linearR = pqEotf(rPrime);
    const double linearG = pqEotf(gPrime);
    const double linearB = pqEotf(bPrime);
    return {
        1.6605 * linearR - 0.5876 * linearG - 0.0728 * linearB,
        -0.1246 * linearR + 1.1329 * linearG - 0.0083 * linearB,
        -0.0182 * linearR - 0.1006 * linearG + 1.1187 * linearB,
    };
}

double toneMapLinearToSdrGamma(double linear) {
    const double nits = std::max(linear, 0.0) * 10000.0 * 1.4;
    const double mapped = nits / (1.0 + nits / 120.0);
    return std::pow(clamp01(mapped / 120.0), 1.0 / 2.2);
}

uint8_t toByte(double value) {
    return static_cast<uint8_t>(std::clamp(value * 255.0 + 0.5, 0.0, 255.0));
}

bool writeBinaryFile(const std::string& path, const std::vector<uint8_t>& data) {
    std::ofstream stream(path, std::ios::binary | std::ios::trunc);
    if (!stream.is_open()) {
        return false;
    }
    stream.write(reinterpret_cast<const char*>(data.data()), static_cast<std::streamsize>(data.size()));
    return stream.good();
}

bool writeMetadataFile(
    const FrameProbeSnapshot& snapshot,
    bool previewWritten,
    bool lumaWritten,
    bool rawWritten
) {
    std::ofstream stream(snapshot.metadataPath, std::ios::binary | std::ios::trunc);
    if (!stream.is_open()) {
        return false;
    }
    stream
        << "source_chroma=" << snapshot.sourceChroma << "\n"
        << "preferred_chroma=" << snapshot.preferredChroma << "\n"
        << "chroma=" << snapshot.chroma << "\n"
        << "width=" << snapshot.width << "\n"
        << "height=" << snapshot.height << "\n"
        << "visible_width=" << snapshot.visibleWidth << "\n"
        << "visible_height=" << snapshot.visibleHeight << "\n"
        << "plane_count=" << snapshot.planeCount << "\n"
        << "pitch0=" << snapshot.pitches[0] << "\n"
        << "pitch1=" << snapshot.pitches[1] << "\n"
        << "pitch2=" << snapshot.pitches[2] << "\n"
        << "pitch3=" << snapshot.pitches[3] << "\n"
        << "line0=" << snapshot.lines[0] << "\n"
        << "line1=" << snapshot.lines[1] << "\n"
        << "line2=" << snapshot.lines[2] << "\n"
        << "line3=" << snapshot.lines[3] << "\n"
        << "preview_written=" << (previewWritten ? "true" : "false") << "\n"
        << "luma_written=" << (lumaWritten ? "true" : "false") << "\n"
        << "raw_written=" << (rawWritten ? "true" : "false") << "\n";
    return stream.good();
}

bool writeLumaPgm(const FrameProbeSnapshot& snapshot) {
    if (snapshot.buffer.empty() || isRgb32(snapshot.chroma) || snapshot.planeCount == 0) {
        return false;
    }
    const unsigned bitDepth = inferBitDepth(snapshot.chroma);
    const double yOffset = bitDepth > 8 ? 64.0 : 16.0;
    const double yRange = bitDepth > 8 ? 876.0 : 219.0;
    const size_t bytesPerSample = bitDepth > 8 ? 2u : 1u;
    std::vector<uint8_t> luma(snapshot.width * snapshot.height);
    const uint8_t* plane = snapshot.buffer.data() + snapshot.offsets[0];
    for (unsigned y = 0; y < snapshot.height; ++y) {
        for (unsigned x = 0; x < snapshot.width; ++x) {
            const size_t sampleOffset = static_cast<size_t>(y) * snapshot.pitches[0] + x * bytesPerSample;
            const double code = static_cast<double>(readSample(plane, sampleOffset, bitDepth));
            const double normalized = clamp01((code - yOffset) / yRange);
            luma[static_cast<size_t>(y) * snapshot.width + x] = toByte(normalized);
        }
    }
    std::ofstream stream(snapshot.lumaPath, std::ios::binary | std::ios::trunc);
    if (!stream.is_open()) {
        return false;
    }
    stream << "P5\n" << snapshot.width << " " << snapshot.height << "\n255\n";
    stream.write(reinterpret_cast<const char*>(luma.data()), static_cast<std::streamsize>(luma.size()));
    return stream.good();
}

bool writePreviewPpm(const FrameProbeSnapshot& snapshot) {
    if (snapshot.buffer.empty()) {
        return false;
    }
    std::vector<uint8_t> pixels(static_cast<size_t>(snapshot.width) * snapshot.height * 3u);
    if (isRgb32(snapshot.chroma)) {
        const uint8_t* plane = snapshot.buffer.data() + snapshot.offsets[0];
        for (unsigned y = 0; y < snapshot.height; ++y) {
            for (unsigned x = 0; x < snapshot.width; ++x) {
                const size_t sourceOffset = static_cast<size_t>(y) * snapshot.pitches[0] + x * 4u;
                const size_t destOffset = (static_cast<size_t>(y) * snapshot.width + x) * 3u;
                pixels[destOffset + 0] = plane[sourceOffset + 2];
                pixels[destOffset + 1] = plane[sourceOffset + 1];
                pixels[destOffset + 2] = plane[sourceOffset + 0];
            }
        }
    } else if (
        isPlanar420(snapshot.chroma) ||
        isHighBitDepthPlanar420(snapshot.chroma) ||
        isSemiPlanar420(snapshot.chroma) ||
        isHighBitDepthSemiPlanar420(snapshot.chroma)
    ) {
        const unsigned bitDepth = inferBitDepth(snapshot.chroma);
        const size_t bytesPerSample = bitDepth > 8 ? 2u : 1u;
        const double yOffset = bitDepth > 8 ? 64.0 : 16.0;
        const double yRange = bitDepth > 8 ? 876.0 : 219.0;
        const double chromaCenter = bitDepth > 8 ? 512.0 : 128.0;
        const double chromaRange = bitDepth > 8 ? 896.0 : 224.0;
        const bool hdrLike = bitDepth > 8;
        const bool bt2020 = usesBt2020Matrix(snapshot.chroma);
        const uint8_t* yPlane = snapshot.buffer.data() + snapshot.offsets[0];
        const uint8_t* uPlane = snapshot.planeCount > 1 ? snapshot.buffer.data() + snapshot.offsets[1] : nullptr;
        const uint8_t* vPlane = snapshot.planeCount > 2 ? snapshot.buffer.data() + snapshot.offsets[2] : nullptr;
        for (unsigned y = 0; y < snapshot.height; ++y) {
            for (unsigned x = 0; x < snapshot.width; ++x) {
                const size_t destOffset = (static_cast<size_t>(y) * snapshot.width + x) * 3u;
                const size_t yOffsetBytes = static_cast<size_t>(y) * snapshot.pitches[0] + x * bytesPerSample;
                const double yCode = static_cast<double>(readSample(yPlane, yOffsetBytes, bitDepth));
                const double yPrime = clamp01((yCode - yOffset) / yRange);
                const unsigned chromaX = x / 2u;
                const unsigned chromaY = y / 2u;
                double uCode = chromaCenter;
                double vCode = chromaCenter;
                if (snapshot.planeCount >= 3 && uPlane != nullptr && vPlane != nullptr) {
                    const size_t uOffset = static_cast<size_t>(chromaY) * snapshot.pitches[1] + chromaX * bytesPerSample;
                    const size_t vOffset = static_cast<size_t>(chromaY) * snapshot.pitches[2] + chromaX * bytesPerSample;
                    uCode = static_cast<double>(readSample(uPlane, uOffset, bitDepth));
                    vCode = static_cast<double>(readSample(vPlane, vOffset, bitDepth));
                } else if (snapshot.planeCount == 2 && uPlane != nullptr) {
                    const size_t uvOffset = static_cast<size_t>(chromaY) * snapshot.pitches[1] + chromaX * bytesPerSample * 2u;
                    if (snapshot.chroma == "NV21") {
                        vCode = static_cast<double>(readSample(uPlane, uvOffset, bitDepth));
                        uCode = static_cast<double>(readSample(uPlane, uvOffset + bytesPerSample, bitDepth));
                    } else {
                        uCode = static_cast<double>(readSample(uPlane, uvOffset, bitDepth));
                        vCode = static_cast<double>(readSample(uPlane, uvOffset + bytesPerSample, bitDepth));
                    }
                }
                const double u = (uCode - chromaCenter) / chromaRange;
                const double v = (vCode - chromaCenter) / chromaRange;
                double rPrime;
                double gPrime;
                double bPrime;
                if (bt2020) {
                    rPrime = yPrime + 1.4746 * v;
                    gPrime = yPrime - 0.164553 * u - 0.571353 * v;
                    bPrime = yPrime + 1.8814 * u;
                } else {
                    rPrime = yPrime + 1.5748 * v;
                    gPrime = yPrime - 0.187324 * u - 0.468124 * v;
                    bPrime = yPrime + 1.8556 * u;
                }
                rPrime = clamp01(rPrime);
                gPrime = clamp01(gPrime);
                bPrime = clamp01(bPrime);
                if (hdrLike) {
                    const auto linearBt709 = bt2020LinearToBt709(rPrime, gPrime, bPrime);
                    rPrime = toneMapLinearToSdrGamma(linearBt709[0]);
                    gPrime = toneMapLinearToSdrGamma(linearBt709[1]);
                    bPrime = toneMapLinearToSdrGamma(linearBt709[2]);
                }
                pixels[destOffset + 0] = toByte(rPrime);
                pixels[destOffset + 1] = toByte(gPrime);
                pixels[destOffset + 2] = toByte(bPrime);
            }
        }
    } else {
        return false;
    }

    std::ofstream stream(snapshot.previewPath, std::ios::binary | std::ios::trunc);
    if (!stream.is_open()) {
        return false;
    }
    stream << "P6\n" << snapshot.width << " " << snapshot.height << "\n255\n";
    stream.write(reinterpret_cast<const char*>(pixels.data()), static_cast<std::streamsize>(pixels.size()));
    return stream.good();
}

FrameProbeSnapshot snapshotFromContext(FrameProbeContext* context) {
    FrameProbeSnapshot snapshot;
    snapshot.metadataPath = context->metadataPath;
    snapshot.previewPath = context->previewPath;
    snapshot.lumaPath = context->lumaPath;
    snapshot.rawFramePath = context->rawFramePath;
    snapshot.sourceChroma = context->sourceChroma;
    snapshot.preferredChroma = context->preferredChroma;
    snapshot.chroma = std::string(context->chroma.data(), context->chroma.data() + 4);
    snapshot.width = context->width;
    snapshot.height = context->height;
    snapshot.visibleWidth = context->visibleWidth;
    snapshot.visibleHeight = context->visibleHeight;
    snapshot.planeCount = context->planeCount;
    snapshot.pitches = context->pitches;
    snapshot.lines = context->lines;
    snapshot.offsets = context->offsets;
    snapshot.buffer.assign(context->buffer, context->buffer + context->totalBytes);
    return snapshot;
}

unsigned frameProbeSetup(
    void** opaque,
    char* chroma,
    unsigned* width,
    unsigned* height,
    unsigned* pitches,
    unsigned* lines
) {
    auto* context = opaque != nullptr ? static_cast<FrameProbeContext*>(*opaque) : nullptr;
    CallbackScope scope(context);
    if (context == nullptr || chroma == nullptr || width == nullptr || height == nullptr ||
        pitches == nullptr || lines == nullptr) {
        return 0;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    freeProbeBuffer(context);
    context->configured = false;
    context->captured = false;
    // libvlc_video_format_cb provides a single width/height value, not a
    // visible-size array. Treating the pointers as arrays corrupts our VMEM
    // layout derivation and produces bogus sizes such as 1090x1090.
    const unsigned codedWidth = *width;
    const unsigned visibleWidth = codedWidth;
    const unsigned codedHeight = *height;
    const unsigned visibleHeight = codedHeight;
    context->width = codedWidth;
    context->height = codedHeight;
    context->visibleWidth = visibleWidth;
    context->visibleHeight = visibleHeight;
    context->sourceChroma = std::string(chroma, chroma + 4);
    const std::string targetFourcc = selectProbeOutputChroma(context, context->sourceChroma);
    std::memcpy(context->chroma.data(), targetFourcc.data(), 4);
    context->chroma[4] = '\0';
    if (targetFourcc != context->sourceChroma) {
        std::memcpy(chroma, targetFourcc.data(), 4);
    }
    for (size_t index = 0; index < 4; ++index) {
        context->pitches[index] = pitches[index];
        context->lines[index] = lines[index];
        context->offsets[index] = 0;
    }
    const std::string fourcc = std::string(context->chroma.data(), context->chroma.data() + 4);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "frameProbeSetup source=%s target=%s preferred=%s coded=%ux%u visible=%ux%u output=%ux%u",
        context->sourceChroma.c_str(),
        fourcc.c_str(),
        context->preferredChroma.c_str(),
        codedWidth,
        codedHeight,
        visibleWidth,
        visibleHeight,
        context->width,
        context->height
    );
    bool needsDefaultLayout = false;
    for (size_t index = 0; index < context->pitches.size(); ++index) {
        if (context->pitches[index] == 0 || context->lines[index] == 0) {
            needsDefaultLayout = true;
            break;
        }
    }
    if (needsDefaultLayout) {
        if (!populateDefaultPlaneLayout(
                fourcc,
                context->width,
                context->height,
                &context->pitches,
                &context->lines)) {
            __android_log_print(
                ANDROID_LOG_ERROR,
                kTag,
                "frameProbeSetup could not derive default layout chroma=%s width=%u height=%u",
                fourcc.c_str(),
                context->width,
                context->height
            );
            return 0;
        }
        for (size_t index = 0; index < 4; ++index) {
            pitches[index] = context->pitches[index];
            lines[index] = context->lines[index];
        }
    }
    context->planeCount = inferPlaneCount(fourcc, context->pitches, context->lines);
    if (context->planeCount == 0) {
        logError("frameProbeSetup could not infer plane count");
        return 0;
    }
    size_t totalBytes = 0;
    for (unsigned index = 0; index < context->planeCount; ++index) {
        if (context->pitches[index] == 0 || context->lines[index] == 0) {
            __android_log_print(
                ANDROID_LOG_ERROR,
                kTag,
                "frameProbeSetup received empty pitch or line entry chroma=%s width=%u height=%u p0=%u p1=%u p2=%u l0=%u l1=%u l2=%u",
                fourcc.c_str(),
                context->width,
                context->height,
                context->pitches[0],
                context->pitches[1],
                context->pitches[2],
                context->lines[0],
                context->lines[1],
                context->lines[2]
            );
            return 0;
        }
        context->offsets[index] = totalBytes;
        totalBytes += static_cast<size_t>(context->pitches[index]) * context->lines[index];
    }
    context->buffer = static_cast<uint8_t*>(std::malloc(totalBytes));
    if (context->buffer == nullptr) {
        logError("frameProbeSetup failed to allocate frame buffer");
        return 0;
    }
    std::memset(context->buffer, 0, totalBytes);
    context->totalBytes = totalBytes;
    context->lockCalls = 0;
    context->unlockCalls = 0;
    context->displayCalls = 0;
    context->configured = true;
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "frameProbeSetup configured chroma=%s planeCount=%u totalBytes=%zu pitch0=%u pitch1=%u pitch2=%u line0=%u line1=%u line2=%u",
        fourcc.c_str(),
        context->planeCount,
        context->totalBytes,
        context->pitches[0],
        context->pitches[1],
        context->pitches[2],
        context->lines[0],
        context->lines[1],
        context->lines[2]
    );
    return 1;
}

void frameProbeCleanup(void* opaque) {
    auto* context = static_cast<FrameProbeContext*>(opaque);
    CallbackScope scope(context);
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    freeProbeBuffer(context);
    context->configured = false;
}

void* frameProbeLock(void* opaque, void** planes) {
    auto* context = static_cast<FrameProbeContext*>(opaque);
    CallbackScope scope(context);
    if (context == nullptr || planes == nullptr) {
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    if (!context->configured || context->released || context->buffer == nullptr) {
        return nullptr;
    }
    context->lockCalls++;
    if (context->lockCalls <= 3) {
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "frameProbeLock call=%u planeCount=%u totalBytes=%zu",
            context->lockCalls,
            context->planeCount,
            context->totalBytes
        );
    }
    for (unsigned index = 0; index < context->planeCount; ++index) {
        planes[index] = context->buffer + context->offsets[index];
    }
    return context->buffer;
}

void frameProbeUnlock(void* opaque, void* /* picture */, void* const* /* planes */) {
    auto* context = static_cast<FrameProbeContext*>(opaque);
    CallbackScope scope(context);
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->unlockCalls++;
    if (context->unlockCalls <= 3) {
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "frameProbeUnlock call=%u",
            context->unlockCalls
        );
    }
}

void frameProbeDisplay(void* opaque, void* /* picture */) {
    auto* context = static_cast<FrameProbeContext*>(opaque);
    CallbackScope scope(context);
    if (context == nullptr) {
        return;
    }
    FrameProbeSnapshot snapshot;
    {
        std::lock_guard<std::mutex> lock(context->mutex);
        if (!context->configured || context->captured || context->released ||
            context->buffer == nullptr || context->totalBytes == 0) {
            return;
        }
        context->displayCalls++;
        if (context->displayCalls <= 3) {
            __android_log_print(
                ANDROID_LOG_INFO,
                kTag,
                "frameProbeDisplay call=%u lockCalls=%u unlockCalls=%u totalBytes=%zu",
                context->displayCalls,
                context->lockCalls,
                context->unlockCalls,
                context->totalBytes
            );
        }
        context->captured = true;
        snapshot = snapshotFromContext(context);
    }

    const bool rawWritten = writeBinaryFile(snapshot.rawFramePath, snapshot.buffer);
    const bool lumaWritten = writeLumaPgm(snapshot);
    const bool previewWritten = writePreviewPpm(snapshot);
    writeMetadataFile(snapshot, previewWritten, lumaWritten, rawWritten);
    logInfo("Captured libVLC VMEM probe frame");
}

void windowResizeCallbackDispatch(void* opaque, unsigned width, unsigned height) {
    auto* context = static_cast<FrameProbeContext*>(opaque);
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->windowWidth = std::max(width, 1u);
    context->windowHeight = std::max(height, 1u);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "frameProbeResizeCallbackDispatch width=%u height=%u",
        context->windowWidth,
        context->windowHeight
    );
}

void frameProbeWindowCallbacks(
    void* opaque,
    libvlc_video_output_resize_cb resizeCb,
    libvlc_video_output_mouse_move_cb /* mouseMoveCb */,
    libvlc_video_output_mouse_press_cb /* mousePressCb */,
    libvlc_video_output_mouse_release_cb /* mouseReleaseCb */,
    void* reportOpaque
) {
    auto* context = static_cast<FrameProbeContext*>(opaque);
    if (context == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(context->mutex);
        context->resizeCallback = resizeCb;
        context->resizeOpaque = reportOpaque;
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "frameProbeWindowCallbacks resizeCb=%p reportOpaque=%p window=%ux%u",
            reinterpret_cast<void*>(resizeCb),
            reportOpaque,
            context->windowWidth,
            context->windowHeight
        );
    }
    if (resizeCb != nullptr && reportOpaque != nullptr) {
        resizeCb(reportOpaque, context->windowWidth, context->windowHeight);
    }
}

struct ResolvedLibVlcVideoFns {
    libvlc_video_set_callbacks_fn setCallbacks = nullptr;
    libvlc_video_set_format_callbacks_fn setFormatCallbacks = nullptr;
    var_create_fn varCreate = nullptr;
    var_set_fn varSet = nullptr;
};

bool resolveVideoFns(void* handle, ResolvedLibVlcVideoFns* outFns) {
    if (handle == nullptr || outFns == nullptr) {
        return false;
    }
    outFns->setCallbacks = reinterpret_cast<libvlc_video_set_callbacks_fn>(
        dlsym(handle, "libvlc_video_set_callbacks")
    );
    outFns->setFormatCallbacks = reinterpret_cast<libvlc_video_set_format_callbacks_fn>(
        dlsym(handle, "libvlc_video_set_format_callbacks")
    );
    outFns->varCreate = reinterpret_cast<var_create_fn>(
        dlsym(handle, "var_Create")
    );
    outFns->varSet = reinterpret_cast<var_set_fn>(
        dlsym(handle, "var_Set")
    );
    return outFns->setCallbacks != nullptr &&
        outFns->setFormatCallbacks != nullptr &&
        outFns->varCreate != nullptr &&
        outFns->varSet != nullptr;
}

bool setAddressVar(
    const ResolvedLibVlcVideoFns& fns,
    libvlc_media_player_t* player,
    const char* name,
    void* value
) {
    if (fns.varCreate == nullptr || fns.varSet == nullptr || player == nullptr || name == nullptr) {
        return false;
    }
    const int createResult = fns.varCreate(reinterpret_cast<vlc_object_t*>(player), name, VLC_VAR_ADDRESS);
    if (createResult != 0) {
        __android_log_print(
            ANDROID_LOG_WARN,
            kTag,
            "var_Create failed name=%s result=%d",
            name,
            createResult
        );
    }
    vlc_value_t varValue{};
    varValue.p_address = value;
    const int setResult = fns.varSet(reinterpret_cast<vlc_object_t*>(player), name, varValue);
    if (setResult != 0) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kTag,
            "var_Set failed name=%s address=%p result=%d",
            name,
            value,
            setResult
        );
        return false;
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "var_Set succeeded name=%s address=%p",
        name,
        value
    );
    return true;
}

bool setStringVar(
    const ResolvedLibVlcVideoFns& fns,
    libvlc_media_player_t* player,
    const char* name,
    const char* value
) {
    if (fns.varCreate == nullptr || fns.varSet == nullptr || player == nullptr || name == nullptr || value == nullptr) {
        return false;
    }
    const int createResult = fns.varCreate(reinterpret_cast<vlc_object_t*>(player), name, VLC_VAR_STRING);
    if (createResult != 0) {
        __android_log_print(
            ANDROID_LOG_WARN,
            kTag,
            "var_Create failed name=%s result=%d",
            name,
            createResult
        );
    }
    vlc_value_t varValue{};
    varValue.psz_string = const_cast<char*>(value);
    const int setResult = fns.varSet(reinterpret_cast<vlc_object_t*>(player), name, varValue);
    if (setResult != 0) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kTag,
            "var_Set failed name=%s value=%s result=%d",
            name,
            value,
            setResult
        );
        return false;
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "var_Set succeeded name=%s value=%s",
        name,
        value
    );
    return true;
}

}  // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_com_miruplay_tv_player_LibVlcNativeFrameProbeBindings_createProbe(
    JNIEnv* env,
    jobject /* this */,
    jstring metadataPath,
    jstring previewPath,
    jstring lumaPath,
    jstring rawFramePath,
    jstring preferredOutputChroma
) {
    ScopedUtfChars metadataChars(env, metadataPath);
    ScopedUtfChars previewChars(env, previewPath);
    ScopedUtfChars lumaChars(env, lumaPath);
    ScopedUtfChars rawChars(env, rawFramePath);
    if (!metadataChars.valid() || !previewChars.valid() || !lumaChars.valid() || !rawChars.valid()) {
        return 0;
    }
    auto* context = new FrameProbeContext();
    context->metadataPath = metadataChars.get();
    context->previewPath = previewChars.get();
    context->lumaPath = lumaChars.get();
    context->rawFramePath = rawChars.get();
    if (preferredOutputChroma != nullptr) {
        ScopedUtfChars preferredChars(env, preferredOutputChroma);
        if (!preferredChars.valid()) {
            delete context;
            return 0;
        }
        context->preferredChroma = preferredChars.get();
    }
    return reinterpret_cast<jlong>(context);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_miruplay_tv_player_LibVlcNativeFrameProbeBindings_attachProbe(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong playerInstance,
    jlong probeHandle,
    jint windowWidth,
    jint windowHeight
) {
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "attachProbe invoked playerInstance=%lld probeHandle=%lld requestedWindow=%dx%d",
        static_cast<long long>(playerInstance),
        static_cast<long long>(probeHandle),
        static_cast<int>(windowWidth),
        static_cast<int>(windowHeight)
    );
    if (playerInstance == 0 || probeHandle == 0) {
        return kErrInvalidArgs;
    }
    void* handle = dlopen("libvlc.so", RTLD_NOW);
    if (handle == nullptr) {
        logError("dlopen(libvlc.so) failed for frame probe attach");
        return kErrDlopenFailed;
    }
    ResolvedLibVlcVideoFns fns;
    if (!resolveVideoFns(handle, &fns)) {
        dlclose(handle);
        logError("dlsym(libvlc_video_set_*callbacks) failed for frame probe attach");
        return kErrSymbolMissing;
    }
    auto* context = reinterpret_cast<FrameProbeContext*>(probeHandle);
    {
        std::lock_guard<std::mutex> lock(context->mutex);
        context->windowWidth = static_cast<unsigned>(std::max(windowWidth, 1));
        context->windowHeight = static_cast<unsigned>(std::max(windowHeight, 1));
    }
    fns.setFormatCallbacks(
        reinterpret_cast<libvlc_media_player_t*>(playerInstance),
        frameProbeSetup,
        frameProbeCleanup
    );
    fns.setCallbacks(
        reinterpret_cast<libvlc_media_player_t*>(playerInstance),
        frameProbeLock,
        frameProbeUnlock,
        frameProbeDisplay,
        context
    );
    auto* player = reinterpret_cast<libvlc_media_player_t*>(playerInstance);
    const bool voutApplied = setStringVar(fns, player, "vout", kDefaultVideoOutputModule);
    const bool windowCallbackApplied = setAddressVar(
        fns,
        player,
        "vout-cb-window-cb",
        reinterpret_cast<void*>(frameProbeWindowCallbacks)
    );
    const bool windowOpaqueApplied = setAddressVar(
        fns,
        player,
        "vout-cb-opaque",
        context
    );
    const bool windowApplied = setStringVar(fns, player, "window", kDefaultWindowModule);
    const bool decDevApplied = setStringVar(fns, player, "dec-dev", kDefaultDecoderDevice);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "attachProbe overrides applied vout=%d window_cb=%d window_opaque=%d window=%d dec-dev=%d window_size=%ux%u",
        voutApplied ? 1 : 0,
        windowCallbackApplied ? 1 : 0,
        windowOpaqueApplied ? 1 : 0,
        windowApplied ? 1 : 0,
        decDevApplied ? 1 : 0,
        context->windowWidth,
        context->windowHeight
    );
    dlclose(handle);
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_miruplay_tv_player_LibVlcNativeFrameProbeBindings_releaseProbe(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong playerInstance,
    jlong probeHandle
) {
    if (probeHandle == 0) {
        return;
    }
    auto* context = reinterpret_cast<FrameProbeContext*>(probeHandle);
    {
        std::lock_guard<std::mutex> lock(context->mutex);
        context->released = true;
    }

    if (playerInstance != 0) {
        void* handle = dlopen("libvlc.so", RTLD_NOW);
        if (handle != nullptr) {
            ResolvedLibVlcVideoFns fns;
            if (resolveVideoFns(handle, &fns)) {
                fns.setCallbacks(
                    reinterpret_cast<libvlc_media_player_t*>(playerInstance),
                    nullptr,
                    nullptr,
                    nullptr,
                    nullptr
                );
                fns.setFormatCallbacks(
                    reinterpret_cast<libvlc_media_player_t*>(playerInstance),
                    nullptr,
                    nullptr
                );
            }
            dlclose(handle);
        }
    }

    {
        std::unique_lock<std::mutex> lock(context->mutex);
        context->callbacksIdle.wait_for(
            lock,
            std::chrono::milliseconds(300),
            [context]() { return context->callbackDepth == 0; }
        );
        freeProbeBuffer(context);
        context->configured = false;
    }

    delete context;
}
