#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <sys/stat.h>
#include <vector>

#include "miruplay_ass_compositor.h"

namespace {

constexpr const char* kLogTag = "MiruLibass";

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kLogTag, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, kLogTag, __VA_ARGS__)

struct ASS_Library;
struct ASS_Renderer;
struct ASS_Track;

struct ASS_Image {
    int w;
    int h;
    int stride;
    unsigned char* bitmap;
    uint32_t color;
    int dst_x;
    int dst_y;
    ASS_Image* next;
    int type;
};

using ass_library_init_fn = ASS_Library* (*)();
using ass_library_done_fn = void (*)(ASS_Library*);
using ass_renderer_init_fn = ASS_Renderer* (*)(ASS_Library*);
using ass_renderer_done_fn = void (*)(ASS_Renderer*);
using ass_read_memory_fn = ASS_Track* (*)(ASS_Library*, char*, size_t, char*);
using ass_free_track_fn = void (*)(ASS_Track*);
using ass_add_font_fn = void (*)(ASS_Library*, char*, char*, int);
using ass_set_fonts_fn = void (*)(ASS_Renderer*, const char*, const char*, int, const char*, int);
using ass_set_frame_size_fn = void (*)(ASS_Renderer*, int, int);
using ass_set_storage_size_fn = void (*)(ASS_Renderer*, int, int);
using ass_set_aspect_ratio_fn = void (*)(ASS_Renderer*, double, double);
using ass_set_use_margins_fn = void (*)(ASS_Renderer*, int);
using ass_set_font_scale_fn = void (*)(ASS_Renderer*, double);
using ass_set_line_spacing_fn = void (*)(ASS_Renderer*, double);
using ass_set_check_readorder_fn = void (*)(ASS_Track*, int);
using ass_process_data_fn = void (*)(ASS_Track*, char*, int);
using ass_flush_events_fn = void (*)(ASS_Track*);
using ass_render_frame_fn = ASS_Image* (*)(ASS_Renderer*, ASS_Track*, long long, int*);

struct LibassApi {
    void* handle = nullptr;
    ass_library_init_fn library_init = nullptr;
    ass_library_done_fn library_done = nullptr;
    ass_renderer_init_fn renderer_init = nullptr;
    ass_renderer_done_fn renderer_done = nullptr;
    ass_read_memory_fn read_memory = nullptr;
    ass_free_track_fn free_track = nullptr;
    ass_add_font_fn add_font = nullptr;
    ass_set_fonts_fn set_fonts = nullptr;
    ass_set_frame_size_fn set_frame_size = nullptr;
    ass_set_storage_size_fn set_storage_size = nullptr;
    ass_set_aspect_ratio_fn set_aspect_ratio = nullptr;
    ass_set_use_margins_fn set_use_margins = nullptr;
    ass_set_font_scale_fn set_font_scale = nullptr;
    ass_set_line_spacing_fn set_line_spacing = nullptr;
    ass_set_check_readorder_fn set_check_readorder = nullptr;
    ass_process_data_fn process_data = nullptr;
    ass_flush_events_fn flush_events = nullptr;
    ass_render_frame_fn render_frame = nullptr;
};

struct Session {
    std::mutex mutex;
    ASS_Library* library = nullptr;
    ASS_Renderer* renderer = nullptr;
    ASS_Track* track = nullptr;
    int frame_width = 0;
    int frame_height = 0;
    int storage_width = 0;
    int storage_height = 0;
    bool rendered_first_frame = false;
    miruplay::ass_compositor::FrameState frame_state{};
};

miruplay::ass_compositor::Image image_view(const ASS_Image* image) {
    if (image == nullptr) return {};
    return {
        image->w,
        image->h,
        image->stride,
        image->bitmap,
        image->color,
        image->dst_x,
        image->dst_y,
    };
}

miruplay::ass_compositor::Rect images_bounds(
    const ASS_Image* images,
    int width,
    int height) {
    miruplay::ass_compositor::Rect result{};
    for (const ASS_Image* image = images; image != nullptr; image = image->next) {
        result = miruplay::ass_compositor::unite(
            result,
            miruplay::ass_compositor::bounds(image_view(image), width, height));
    }
    return result;
}

miruplay::ass_compositor::Buffer buffer_view(ANativeWindow_Buffer* buffer) {
    if (buffer == nullptr) return {};
    return {
        static_cast<uint8_t*>(buffer->bits),
        buffer->width,
        buffer->height,
        buffer->stride,
    };
}

bool valid_rgba_buffer(const ANativeWindow_Buffer& buffer, int width, int height) {
    return buffer.bits != nullptr && buffer.format == WINDOW_FORMAT_RGBA_8888 &&
        buffer.width == width && buffer.height == height && buffer.stride >= buffer.width;
}

LibassApi g_api;
std::once_flag g_api_once;
bool g_api_available = false;

template <typename T>
bool load_symbol(void* handle, const char* name, T* target) {
    *target = reinterpret_cast<T>(dlsym(handle, name));
    if (*target == nullptr) {
        LOGE("Missing libass symbol: %s", name);
        return false;
    }
    return true;
}

bool ensure_api() {
    std::call_once(g_api_once, [] {
        g_api.handle = dlopen("libmpv.so", RTLD_NOW | RTLD_LOCAL);
        if (g_api.handle == nullptr) {
            LOGE("Could not open libmpv.so: %s", dlerror());
            return;
        }

        bool ok = true;
        ok &= load_symbol(g_api.handle, "ass_library_init", &g_api.library_init);
        ok &= load_symbol(g_api.handle, "ass_library_done", &g_api.library_done);
        ok &= load_symbol(g_api.handle, "ass_renderer_init", &g_api.renderer_init);
        ok &= load_symbol(g_api.handle, "ass_renderer_done", &g_api.renderer_done);
        ok &= load_symbol(g_api.handle, "ass_read_memory", &g_api.read_memory);
        ok &= load_symbol(g_api.handle, "ass_free_track", &g_api.free_track);
        ok &= load_symbol(g_api.handle, "ass_add_font", &g_api.add_font);
        ok &= load_symbol(g_api.handle, "ass_set_fonts", &g_api.set_fonts);
        ok &= load_symbol(g_api.handle, "ass_set_frame_size", &g_api.set_frame_size);
        ok &= load_symbol(g_api.handle, "ass_set_storage_size", &g_api.set_storage_size);
        ok &= load_symbol(g_api.handle, "ass_set_aspect_ratio", &g_api.set_aspect_ratio);
        ok &= load_symbol(g_api.handle, "ass_set_use_margins", &g_api.set_use_margins);
        ok &= load_symbol(g_api.handle, "ass_set_font_scale", &g_api.set_font_scale);
        ok &= load_symbol(g_api.handle, "ass_set_line_spacing", &g_api.set_line_spacing);
        ok &= load_symbol(g_api.handle, "ass_set_check_readorder", &g_api.set_check_readorder);
        ok &= load_symbol(g_api.handle, "ass_process_data", &g_api.process_data);
        ok &= load_symbol(g_api.handle, "ass_flush_events", &g_api.flush_events);
        ok &= load_symbol(g_api.handle, "ass_render_frame", &g_api.render_frame);
        g_api_available = ok;
        if (ok) LOGI("Resolved libass API from packaged libmpv.so");
    });
    return g_api_available;
}

const char* find_default_font() {
    static constexpr const char* candidates[] = {
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
    };
    struct stat info {};
    for (const char* candidate : candidates) {
        if (stat(candidate, &info) == 0 && info.st_size > 0) return candidate;
    }
    return nullptr;
}

void release_session(Session* session) {
    if (session == nullptr) return;
    {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (session->track != nullptr && g_api.free_track != nullptr) {
            g_api.free_track(session->track);
            session->track = nullptr;
        }
        if (session->renderer != nullptr && g_api.renderer_done != nullptr) {
            g_api.renderer_done(session->renderer);
            session->renderer = nullptr;
        }
        if (session->library != nullptr && g_api.library_done != nullptr) {
            g_api.library_done(session->library);
            session->library = nullptr;
        }
    }
    delete session;
}

bool clear_window(ANativeWindow* window, int width, int height) {
    if (window == nullptr || width <= 0 || height <= 0) return false;
    if (ANativeWindow_setBuffersGeometry(window, width, height, WINDOW_FORMAT_RGBA_8888) != 0) {
        LOGW("Could not configure subtitle surface %dx%d", width, height);
        return false;
    }
    ANativeWindow_Buffer buffer {};
    ARect dirty {0, 0, width, height};
    if (ANativeWindow_lock(window, &buffer, &dirty) != 0) {
        LOGW("Could not lock subtitle surface");
        return false;
    }
    if (!valid_rgba_buffer(buffer, width, height)) {
        LOGW(
            "Unexpected subtitle buffer geometry/format actual=%dx%d stride=%d format=%d",
            buffer.width,
            buffer.height,
            buffer.stride,
            buffer.format);
        ANativeWindow_unlockAndPost(window);
        return false;
    }
    miruplay::ass_compositor::clear(
        buffer_view(&buffer),
        miruplay::ass_compositor::clamp_rect(
            {dirty.left, dirty.top, dirty.right, dirty.bottom},
            buffer.width,
            buffer.height));
    return ANativeWindow_unlockAndPost(window) == 0;
}

bool copy_byte_array(JNIEnv* env, jbyteArray source, std::vector<char>* target) {
    if (source == nullptr || target == nullptr) return false;
    const jsize size = env->GetArrayLength(source);
    if (size <= 0) return false;
    target->resize(static_cast<size_t>(size));
    env->GetByteArrayRegion(source, 0, size, reinterpret_cast<jbyte*>(target->data()));
    return !env->ExceptionCheck();
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_is_xyz_mpv_subtitle_JniNativeAssCalls_nativeIsAvailable(JNIEnv*, jobject) {
    return ensure_api() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_is_xyz_mpv_subtitle_JniNativeAssCalls_nativeCreate(
    JNIEnv* env,
    jobject,
    jbyteArray document,
    jobjectArray font_names,
    jobjectArray font_data) {
    if (!ensure_api()) return 0;

    std::vector<char> document_bytes;
    if (!copy_byte_array(env, document, &document_bytes)) return 0;

    auto* session = new Session();
    session->library = g_api.library_init();
    if (session->library == nullptr) {
        release_session(session);
        return 0;
    }

    const jsize name_count = font_names == nullptr ? 0 : env->GetArrayLength(font_names);
    const jsize data_count = font_data == nullptr ? 0 : env->GetArrayLength(font_data);
    const jsize font_count = std::min(name_count, data_count);
    for (jsize index = 0; index < font_count; ++index) {
        auto* name = static_cast<jstring>(env->GetObjectArrayElement(font_names, index));
        auto* data = static_cast<jbyteArray>(env->GetObjectArrayElement(font_data, index));
        if (name == nullptr || data == nullptr) {
            if (name != nullptr) env->DeleteLocalRef(name);
            if (data != nullptr) env->DeleteLocalRef(data);
            continue;
        }

        const char* name_chars = env->GetStringUTFChars(name, nullptr);
        std::vector<char> font_bytes;
        if (name_chars != nullptr && copy_byte_array(env, data, &font_bytes)) {
            g_api.add_font(
                session->library,
                const_cast<char*>(name_chars),
                font_bytes.data(),
                static_cast<int>(font_bytes.size()));
        }
        if (name_chars != nullptr) env->ReleaseStringUTFChars(name, name_chars);
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(data);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            release_session(session);
            return 0;
        }
    }

    session->renderer = g_api.renderer_init(session->library);
    if (session->renderer == nullptr) {
        release_session(session);
        return 0;
    }
    g_api.set_fonts(session->renderer, find_default_font(), "sans-serif", 1, nullptr, 1);
    g_api.set_use_margins(session->renderer, 0);
    g_api.set_font_scale(session->renderer, 1.0);
    g_api.set_line_spacing(session->renderer, 0.0);

    document_bytes.push_back('\0');
    session->track = g_api.read_memory(
        session->library,
        document_bytes.data(),
        document_bytes.size() - 1U,
        nullptr);
    if (session->track == nullptr) {
        release_session(session);
        return 0;
    }
    g_api.set_check_readorder(session->track, 0);
    LOGI("Created ASS renderer document_bytes=%zu fonts=%d", document_bytes.size() - 1U, font_count);
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_is_xyz_mpv_subtitle_JniNativeAssCalls_nativeAddEvent(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray dialogue_line) {
    auto* session = reinterpret_cast<Session*>(handle);
    if (session == nullptr || !ensure_api()) return JNI_FALSE;
    std::vector<char> line;
    if (!copy_byte_array(env, dialogue_line, &line)) return JNI_FALSE;
    line.push_back('\n');
    std::lock_guard<std::mutex> lock(session->mutex);
    if (session->track == nullptr) return JNI_FALSE;
    g_api.process_data(session->track, line.data(), static_cast<int>(line.size()));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_is_xyz_mpv_subtitle_JniNativeAssCalls_nativeFlushEvents(
    JNIEnv*,
    jobject,
    jlong handle) {
    auto* session = reinterpret_cast<Session*>(handle);
    if (session == nullptr || !ensure_api()) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(session->mutex);
    if (session->track == nullptr) return JNI_FALSE;
    g_api.flush_events(session->track);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_is_xyz_mpv_subtitle_JniNativeAssCalls_nativeRender(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject surface,
    jlong time_ms,
    jint frame_width,
    jint frame_height,
    jint storage_width,
    jint storage_height) {
    auto* session = reinterpret_cast<Session*>(handle);
    if (session == nullptr || surface == nullptr || !ensure_api() ||
        frame_width <= 0 || frame_height <= 0) {
        return -1;
    }

    std::lock_guard<std::mutex> lock(session->mutex);
    if (session->renderer == nullptr || session->track == nullptr) return -1;

    if (session->frame_width != frame_width || session->frame_height != frame_height) {
        session->frame_width = frame_width;
        session->frame_height = frame_height;
        g_api.set_frame_size(session->renderer, frame_width, frame_height);
    }
    if (storage_width > 0 && storage_height > 0 &&
        (session->storage_width != storage_width || session->storage_height != storage_height)) {
        session->storage_width = storage_width;
        session->storage_height = storage_height;
        g_api.set_storage_size(session->renderer, storage_width, storage_height);
    }
    const double display_aspect = static_cast<double>(frame_width) / frame_height;
    const double storage_aspect = storage_width > 0 && storage_height > 0
        ? static_cast<double>(storage_width) / storage_height
        : 1.0;
    g_api.set_aspect_ratio(session->renderer, display_aspect, storage_aspect);

    int changed = 0;
    ASS_Image* images = g_api.render_frame(session->renderer, session->track, time_ms, &changed);
    if (!session->rendered_first_frame) {
        int image_count = 0;
        for (ASS_Image* image = images; image != nullptr; image = image->next) ++image_count;
        LOGI(
            "Rendered first ASS frame time_ms=%lld frame=%dx%d storage=%dx%d images=%d changed=%d",
            static_cast<long long>(time_ms),
            frame_width,
            frame_height,
            storage_width,
            storage_height,
            image_count,
            changed);
        session->rendered_first_frame = true;
    }

    const auto content = images_bounds(images, frame_width, frame_height);
    const auto plan = miruplay::ass_compositor::plan_frame(
        session->frame_state,
        changed,
        frame_width,
        frame_height,
        content);
    // Unchanged frames do not acquire, lock, clear, blend, or post a surface buffer.
    if (!plan.post) return 0;

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) return -1;
    if (plan.configure_geometry &&
        ANativeWindow_setBuffersGeometry(
            window,
            frame_width,
            frame_height,
            WINDOW_FORMAT_RGBA_8888) != 0) {
        ANativeWindow_release(window);
        return -1;
    }

    ANativeWindow_Buffer buffer {};
    ARect dirty {plan.dirty.left, plan.dirty.top, plan.dirty.right, plan.dirty.bottom};
    if (ANativeWindow_lock(window, &buffer, &dirty) != 0) {
        ANativeWindow_release(window);
        return -1;
    }
    if (!valid_rgba_buffer(buffer, frame_width, frame_height)) {
        LOGW(
            "Unexpected subtitle buffer geometry/format actual=%dx%d stride=%d format=%d",
            buffer.width,
            buffer.height,
            buffer.stride,
            buffer.format);
        ANativeWindow_unlockAndPost(window);
        ANativeWindow_release(window);
        return -1;
    }

    auto actual_dirty = miruplay::ass_compositor::clamp_rect(
        {dirty.left, dirty.top, dirty.right, dirty.bottom},
        buffer.width,
        buffer.height);
    if (!miruplay::ass_compositor::contains(actual_dirty, plan.dirty)) {
        // The NDK contract says the returned rect contains the requested damage.
        // Redrawing the full buffer keeps a non-conforming producer transparent.
        actual_dirty = miruplay::ass_compositor::full_rect(buffer.width, buffer.height);
    }
    const auto target = buffer_view(&buffer);
    miruplay::ass_compositor::clear(target, actual_dirty);
    for (ASS_Image* image = images; image != nullptr; image = image->next) {
        miruplay::ass_compositor::blend(target, image_view(image));
    }
    const int post_result = ANativeWindow_unlockAndPost(window);
    ANativeWindow_release(window);
    if (post_result != 0) return -1;

    miruplay::ass_compositor::commit_frame(
        &session->frame_state,
        frame_width,
        frame_height,
        content);
    return 1;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_is_xyz_mpv_subtitle_JniNativeAssCalls_nativeClearSurface(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject surface,
    jint width,
    jint height) {
    if (surface == nullptr) return JNI_FALSE;
    auto* session = reinterpret_cast<Session*>(handle);
    if (session != nullptr) {
        std::lock_guard<std::mutex> lock(session->mutex);
        // Force the next render to repost even when libass reports unchanged.
        session->frame_state = {};
    }
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) return JNI_FALSE;
    const bool cleared = clear_window(window, width, height);
    ANativeWindow_release(window);
    return cleared ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_is_xyz_mpv_subtitle_JniNativeAssCalls_nativeRelease(
    JNIEnv*,
    jobject,
    jlong handle) {
    release_session(reinterpret_cast<Session*>(handle));
}
