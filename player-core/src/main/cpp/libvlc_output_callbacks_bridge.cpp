#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>

#include <algorithm>
#include <cstdint>
#include <mutex>

namespace {

constexpr const char* kTag = "MiruPlayVlcOutput";

using libvlc_media_player_t = void;

enum libvlc_video_engine_t {
    libvlc_video_engine_disable = 0,
    libvlc_video_engine_opengl = 1,
    libvlc_video_engine_gles2 = 2,
    libvlc_video_engine_d3d11 = 3,
    libvlc_video_engine_d3d9 = 4,
    libvlc_video_engine_anw = 5,
};

enum libvlc_video_color_space_t {
    libvlc_video_colorspace_BT601 = 1,
    libvlc_video_colorspace_BT709 = 2,
    libvlc_video_colorspace_BT2020 = 3,
};

enum libvlc_video_color_primaries_t {
    libvlc_video_primaries_BT601_525 = 1,
    libvlc_video_primaries_BT601_625 = 2,
    libvlc_video_primaries_BT709 = 3,
    libvlc_video_primaries_BT2020 = 4,
};

enum libvlc_video_transfer_func_t {
    libvlc_video_transfer_func_LINEAR = 1,
    libvlc_video_transfer_func_SRGB = 2,
    libvlc_video_transfer_func_BT470_BG = 3,
    libvlc_video_transfer_func_BT470_M = 4,
    libvlc_video_transfer_func_BT709 = 5,
    libvlc_video_transfer_func_PQ = 6,
    libvlc_video_transfer_func_SMPTE_240 = 7,
    libvlc_video_transfer_func_HLG = 8,
};

enum libvlc_video_orient_t {
    libvlc_video_orient_top_left = 0,
};

struct libvlc_video_setup_device_cfg_t {
    bool hardware_decoding;
};

struct libvlc_video_setup_device_info_t {
    union {
        struct {
            void* device_context;
            void* context_mutex;
        } d3d11;
        struct {
            void* device;
            int adapter;
        } d3d9;
    };
};

struct libvlc_video_render_cfg_t {
    unsigned width;
    unsigned height;
    unsigned bitdepth;
    bool full_range;
    libvlc_video_color_space_t colorspace;
    libvlc_video_color_primaries_t primaries;
    libvlc_video_transfer_func_t transfer;
    void* device;
};

struct libvlc_video_output_cfg_t {
    union {
        int dxgi_format;
        uint32_t d3d9_format;
        int opengl_format;
        void* p_surface;
        struct {
            void* video;
            void* subtitle;
        } anw;
    };
    bool full_range;
    libvlc_video_color_space_t colorspace;
    libvlc_video_color_primaries_t primaries;
    libvlc_video_transfer_func_t transfer;
    libvlc_video_orient_t orientation;
};

using libvlc_video_output_resize_cb = void (*)(void*, unsigned, unsigned);
using libvlc_video_output_mouse_move_cb = void (*)(void*, int, int);
using libvlc_video_output_mouse_press_cb = void (*)(void*, int);
using libvlc_video_output_mouse_release_cb = void (*)(void*, int);
using libvlc_video_output_setup_cb = bool (*)(
    void**,
    const libvlc_video_setup_device_cfg_t*,
    libvlc_video_setup_device_info_t*
);
using libvlc_video_output_cleanup_cb = void (*)(void*);
using libvlc_video_output_set_window_cb = void (*)(
    void*,
    libvlc_video_output_resize_cb,
    libvlc_video_output_mouse_move_cb,
    libvlc_video_output_mouse_press_cb,
    libvlc_video_output_mouse_release_cb,
    void*
);
using libvlc_video_update_output_cb = bool (*)(
    void*,
    const libvlc_video_render_cfg_t*,
    libvlc_video_output_cfg_t*
);
using libvlc_video_swap_cb = void (*)(void*);
using libvlc_video_makeCurrent_cb = bool (*)(void*, bool);
using libvlc_video_getProcAddress_cb = void* (*)(void*, const char*);
using libvlc_video_frameMetadata_cb = void (*)(void*, int, const void*);
using libvlc_video_output_select_plane_cb = bool (*)(void*, size_t, void*);

using libvlc_video_set_output_callbacks_fn = bool (*)(
    libvlc_media_player_t*,
    libvlc_video_engine_t,
    libvlc_video_output_setup_cb,
    libvlc_video_output_cleanup_cb,
    libvlc_video_output_set_window_cb,
    libvlc_video_update_output_cb,
    libvlc_video_swap_cb,
    libvlc_video_makeCurrent_cb,
    libvlc_video_getProcAddress_cb,
    libvlc_video_frameMetadata_cb,
    libvlc_video_output_select_plane_cb,
    void*
);
using asurface_control_create_from_window_fn = void* (*)(ANativeWindow*, const char*);
using asurface_control_release_fn = void (*)(void*);

struct OutputCallbackContext {
    std::mutex mutex;
    ANativeWindow* nativeWindow = nullptr;
    int windowWidth = 0;
    int windowHeight = 0;
    unsigned videoWidth = 0U;
    unsigned videoHeight = 0U;
    unsigned bitDepth = 0U;
    bool fullRange = false;
    libvlc_video_color_space_t colorspace = libvlc_video_colorspace_BT709;
    libvlc_video_color_primaries_t primaries = libvlc_video_primaries_BT709;
    libvlc_video_transfer_func_t transfer = libvlc_video_transfer_func_SRGB;
    libvlc_video_output_resize_cb resizeCallback = nullptr;
    void* resizeOpaque = nullptr;
};

void logError(const char* message) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message);
}

void logContextInfoLocked(const OutputCallbackContext* context, const char* reason) {
    const int nativeWidth = context->nativeWindow != nullptr
        ? ANativeWindow_getWidth(context->nativeWindow)
        : 0;
    const int nativeHeight = context->nativeWindow != nullptr
        ? ANativeWindow_getHeight(context->nativeWindow)
        : 0;
    const int nativeFormat = context->nativeWindow != nullptr
        ? ANativeWindow_getFormat(context->nativeWindow)
        : 0;
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "reason=%s native_window=%p window=%dx%d native=%dx%d format=%d video=%ux%u bitdepth=%u full_range=%d colorspace=%d primaries=%d transfer=%d",
        reason,
        context->nativeWindow,
        context->windowWidth,
        context->windowHeight,
        nativeWidth,
        nativeHeight,
        nativeFormat,
        context->videoWidth,
        context->videoHeight,
        context->bitDepth,
        context->fullRange ? 1 : 0,
        static_cast<int>(context->colorspace),
        static_cast<int>(context->primaries),
        static_cast<int>(context->transfer)
    );
}

void releaseNativeWindow(OutputCallbackContext* context) {
    if (context->nativeWindow != nullptr) {
        ANativeWindow_release(context->nativeWindow);
        context->nativeWindow = nullptr;
    }
}

struct WindowResizeDispatch {
    libvlc_video_output_resize_cb callback = nullptr;
    void* opaque = nullptr;
    unsigned width = 0U;
    unsigned height = 0U;
};

WindowResizeDispatch collectWindowResizeDispatchLocked(OutputCallbackContext* context) {
    WindowResizeDispatch dispatch;
    if (context->resizeCallback == nullptr ||
        context->windowWidth <= 0 ||
        context->windowHeight <= 0) {
        return dispatch;
    }
    dispatch.callback = context->resizeCallback;
    dispatch.opaque = context->resizeOpaque;
    dispatch.width = static_cast<unsigned>(context->windowWidth);
    dispatch.height = static_cast<unsigned>(context->windowHeight);
    return dispatch;
}

void dispatchWindowResize(
    const WindowResizeDispatch& dispatch,
    const char* reason
) {
    if (dispatch.callback == nullptr || dispatch.width == 0U || dispatch.height == 0U) {
        return;
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "dispatch_window_resize reason=%s width=%u height=%u opaque=%p callback=%p",
        reason,
        dispatch.width,
        dispatch.height,
        dispatch.opaque,
        reinterpret_cast<void*>(dispatch.callback)
    );
    dispatch.callback(dispatch.opaque, dispatch.width, dispatch.height);
}

void updateNativeWindowGeometryLocked(OutputCallbackContext* context, int width, int height) {
    if (context->nativeWindow == nullptr) {
        return;
    }
    context->windowWidth = std::max(width, 1);
    context->windowHeight = std::max(height, 1);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "updated_window_hint window=%dx%d native_window=%p native=%dx%d format=%d",
        context->windowWidth,
        context->windowHeight,
        context->nativeWindow,
        ANativeWindow_getWidth(context->nativeWindow),
        ANativeWindow_getHeight(context->nativeWindow),
        ANativeWindow_getFormat(context->nativeWindow)
    );
}

void outputSetWindowCallbacks(
    void* opaque,
    libvlc_video_output_resize_cb resizeCb,
    libvlc_video_output_mouse_move_cb /* mouseMoveCb */,
    libvlc_video_output_mouse_press_cb /* mousePressCb */,
    libvlc_video_output_mouse_release_cb /* mouseReleaseCb */,
    void* reportOpaque
) {
    auto* context = static_cast<OutputCallbackContext*>(opaque);
    if (context == nullptr) {
        return;
    }
    WindowResizeDispatch dispatch;
    {
        std::lock_guard<std::mutex> lock(context->mutex);
        context->resizeCallback = resizeCb;
        context->resizeOpaque = reportOpaque;
        logContextInfoLocked(
            context,
            resizeCb != nullptr ? "window_callbacks_registered" : "window_callbacks_cleared"
        );
        dispatch = collectWindowResizeDispatchLocked(context);
    }
    dispatchWindowResize(dispatch, "window_callbacks_registered");
}

void logSurfaceControlProbe(ANativeWindow* nativeWindow) {
    if (nativeWindow == nullptr) {
        return;
    }
    void* androidHandle = dlopen("libandroid.so", RTLD_NOW);
    if (androidHandle == nullptr) {
        __android_log_print(
            ANDROID_LOG_WARN,
            kTag,
            "surface_control_probe dlopen(libandroid.so) failed"
        );
        return;
    }
    auto* createFromWindow = reinterpret_cast<asurface_control_create_from_window_fn>(
        dlsym(androidHandle, "ASurfaceControl_createFromWindow")
    );
    auto* releaseSurfaceControl = reinterpret_cast<asurface_control_release_fn>(
        dlsym(androidHandle, "ASurfaceControl_release")
    );
    if (createFromWindow == nullptr || releaseSurfaceControl == nullptr) {
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "surface_control_probe symbols missing create=%p release=%p",
            createFromWindow,
            releaseSurfaceControl
        );
        dlclose(androidHandle);
        return;
    }
    void* surfaceControl = createFromWindow(nativeWindow, "miruplay_output_probe");
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "surface_control_probe native_window=%p result=%p width=%d height=%d format=%d",
        nativeWindow,
        surfaceControl,
        ANativeWindow_getWidth(nativeWindow),
        ANativeWindow_getHeight(nativeWindow),
        ANativeWindow_getFormat(nativeWindow)
    );
    if (surfaceControl != nullptr) {
        releaseSurfaceControl(surfaceControl);
    }
    dlclose(androidHandle);
}

bool outputSetup(
    void** opaque,
    const libvlc_video_setup_device_cfg_t* cfg,
    libvlc_video_setup_device_info_t* /* out */
) {
    if (opaque == nullptr || *opaque == nullptr) {
        return false;
    }
    auto* context = static_cast<OutputCallbackContext*>(*opaque);
    std::lock_guard<std::mutex> lock(context->mutex);
    if (context->nativeWindow == nullptr) {
        logError("ANW output setup missing native window");
        return false;
    }
    if (context->windowWidth > 0 && context->windowHeight > 0) {
        updateNativeWindowGeometryLocked(context, context->windowWidth, context->windowHeight);
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "output_setup hardware_decoding=%d native_window=%p",
        cfg != nullptr && cfg->hardware_decoding ? 1 : 0,
        context->nativeWindow
    );
    logContextInfoLocked(context, "output_setup");
    return true;
}

void outputCleanup(void* opaque) {
    auto* context = static_cast<OutputCallbackContext*>(opaque);
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->resizeCallback = nullptr;
    context->resizeOpaque = nullptr;
    logContextInfoLocked(context, "output_cleanup");
    releaseNativeWindow(context);
}

bool outputUpdate(
    void* opaque,
    const libvlc_video_render_cfg_t* cfg,
    libvlc_video_output_cfg_t* output
) {
    auto* context = static_cast<OutputCallbackContext*>(opaque);
    if (context == nullptr || cfg == nullptr || output == nullptr) {
        return false;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    if (context->nativeWindow == nullptr) {
        logError("ANW output update missing native window");
        return false;
    }
    if (context->windowWidth <= 0 || context->windowHeight <= 0) {
        const int fallbackWidth = std::max(
            static_cast<int>(cfg->width),
            ANativeWindow_getWidth(context->nativeWindow)
        );
        const int fallbackHeight = std::max(
            static_cast<int>(cfg->height),
            ANativeWindow_getHeight(context->nativeWindow)
        );
        updateNativeWindowGeometryLocked(context, fallbackWidth, fallbackHeight);
    }
    context->videoWidth = cfg->width;
    context->videoHeight = cfg->height;
    context->bitDepth = cfg->bitdepth;
    context->fullRange = cfg->full_range;
    context->colorspace = cfg->colorspace;
    context->primaries = cfg->primaries;
    context->transfer = cfg->transfer;

    output->anw.video = context->nativeWindow;
    output->anw.subtitle = nullptr;
    output->full_range = cfg->full_range;
    output->colorspace = cfg->colorspace;
    output->primaries = cfg->primaries;
    output->transfer = cfg->transfer;
    output->orientation = libvlc_video_orient_top_left;

    logContextInfoLocked(context, "output_update");
    return true;
}

libvlc_video_set_output_callbacks_fn resolveSetOutputCallbacks() {
    void* handle = dlopen("libvlc.so", RTLD_NOW);
    if (handle == nullptr) {
        logError("dlopen(libvlc.so) failed");
        return nullptr;
    }
    auto* function = reinterpret_cast<libvlc_video_set_output_callbacks_fn>(
        dlsym(handle, "libvlc_video_set_output_callbacks")
    );
    if (function == nullptr) {
        logError("dlsym(libvlc_video_set_output_callbacks) failed");
    }
    return function;
}

}  // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_com_miruplay_tv_player_LibVlcNativeOutputCallbacksBindings_attachOutput(
    JNIEnv* env,
    jobject /* this */,
    jlong playerInstance,
    jobject surface,
    jint width,
    jint height
) {
    if (playerInstance == 0 || surface == nullptr) {
        return 0L;
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "attach_output begin player=%lld requested_window=%dx%d",
        static_cast<long long>(playerInstance),
        static_cast<int>(width),
        static_cast<int>(height)
    );
    auto* setOutputCallbacks = resolveSetOutputCallbacks();
    if (setOutputCallbacks == nullptr) {
        return 0L;
    }
    auto* context = new OutputCallbackContext();
    context->nativeWindow = ANativeWindow_fromSurface(env, surface);
    if (context->nativeWindow == nullptr) {
        delete context;
        logError("ANativeWindow_fromSurface failed");
        return 0L;
    }
    updateNativeWindowGeometryLocked(context, static_cast<int>(width), static_cast<int>(height));
    logSurfaceControlProbe(context->nativeWindow);
    const bool attached = setOutputCallbacks(
        reinterpret_cast<libvlc_media_player_t*>(playerInstance),
        libvlc_video_engine_anw,
        outputSetup,
        outputCleanup,
        outputSetWindowCallbacks,
        outputUpdate,
        nullptr,
        nullptr,
        nullptr,
        nullptr,
        nullptr,
        context
    );
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "attach_output set_output_callbacks returned attached=%d",
        attached ? 1 : 0
    );
    if (!attached) {
        releaseNativeWindow(context);
        delete context;
        logError("libVLC refused ANW output callbacks");
        return 0L;
    }
    {
        std::lock_guard<std::mutex> lock(context->mutex);
        logContextInfoLocked(context, "attached");
    }
    return reinterpret_cast<jlong>(context);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_miruplay_tv_player_LibVlcNativeOutputCallbacksBindings_updateOutputWindow(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong bridgeHandle,
    jint width,
    jint height
) {
    if (bridgeHandle == 0) {
        return;
    }
    auto* context = reinterpret_cast<OutputCallbackContext*>(bridgeHandle);
    WindowResizeDispatch dispatch;
    {
        std::lock_guard<std::mutex> lock(context->mutex);
        updateNativeWindowGeometryLocked(context, static_cast<int>(width), static_cast<int>(height));
        logContextInfoLocked(context, "window_resize");
        dispatch = collectWindowResizeDispatchLocked(context);
    }
    dispatchWindowResize(dispatch, "window_resize");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_miruplay_tv_player_LibVlcNativeOutputCallbacksBindings_releaseOutput(
    JNIEnv* /* env */,
    jobject /* this */,
    jlong playerInstance,
    jlong bridgeHandle
) {
    if (bridgeHandle == 0) {
        return;
    }
    auto* context = reinterpret_cast<OutputCallbackContext*>(bridgeHandle);
    if (playerInstance != 0) {
        if (auto* setOutputCallbacks = resolveSetOutputCallbacks()) {
            setOutputCallbacks(
                reinterpret_cast<libvlc_media_player_t*>(playerInstance),
                libvlc_video_engine_disable,
                nullptr,
                nullptr,
                nullptr,
                nullptr,
                nullptr,
                nullptr,
                nullptr,
                nullptr,
                nullptr,
                nullptr
            );
        }
    }
    {
        std::lock_guard<std::mutex> lock(context->mutex);
        releaseNativeWindow(context);
        logContextInfoLocked(context, "released");
    }
    // Keep the context allocation alive for the rest of the process to avoid
    // a use-after-free if libVLC finishes tearing down the callback vout after
    // this release call returns.
}
