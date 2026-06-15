#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

namespace {

constexpr const char* kTag = "MiruPlayVlcBridge";
constexpr int kErrInvalidArgs = -1;
constexpr int kErrDlopenFailed = -2;
constexpr int kErrSymbolMissing = -3;

using libvlc_media_player_t = void;
using libvlc_video_take_snapshot_fn = int (*)(
    libvlc_media_player_t*,
    unsigned,
    const char*,
    unsigned,
    unsigned
);

void logError(const char* message) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message);
}

}  // namespace

extern "C"
JNIEXPORT jint JNICALL
Java_com_miruplay_tv_player_LibVlcNativeSnapshotBindings_takeSnapshot(
    JNIEnv* env,
    jobject /* this */,
    jlong playerInstance,
    jstring outputPath,
    jint width,
    jint height
) {
    if (playerInstance == 0 || outputPath == nullptr) {
        return kErrInvalidArgs;
    }
    const char* outputPathChars = env->GetStringUTFChars(outputPath, nullptr);
    if (outputPathChars == nullptr) {
        return kErrInvalidArgs;
    }

    void* handle = dlopen("libvlc.so", RTLD_NOW);
    if (handle == nullptr) {
        env->ReleaseStringUTFChars(outputPath, outputPathChars);
        logError("dlopen(libvlc.so) failed");
        return kErrDlopenFailed;
    }

    auto* takeSnapshot = reinterpret_cast<libvlc_video_take_snapshot_fn>(
        dlsym(handle, "libvlc_video_take_snapshot")
    );
    if (takeSnapshot == nullptr) {
        dlclose(handle);
        env->ReleaseStringUTFChars(outputPath, outputPathChars);
        logError("dlsym(libvlc_video_take_snapshot) failed");
        return kErrSymbolMissing;
    }

    const int result = takeSnapshot(
        reinterpret_cast<libvlc_media_player_t*>(playerInstance),
        0U,
        outputPathChars,
        static_cast<unsigned>(width < 0 ? 0 : width),
        static_cast<unsigned>(height < 0 ? 0 : height)
    );

    dlclose(handle);
    env->ReleaseStringUTFChars(outputPath, outputPathChars);
    return result;
}
