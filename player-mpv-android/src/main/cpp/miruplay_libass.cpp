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

struct BBox {
    int x = 0;
    int y = 0;
    int w = 0;
    int h = 0;
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
    // Surface work is skipped when libass reports the output unchanged. These
    // fields track what the subtitle surface currently shows so a repost only
    // happens when the image or the geometry actually changes.
    bool surface_has_content = false;
    int posted_width = 0;
    int posted_height = 0;
    BBox last_dirty{};      // bbox of the images last posted
    bool have_last_dirty = false;
};

BBox images_bbox(const ASS_Image* images) {
    BBox bbox{};
    bool first = true;
    for (const ASS_Image* image = images; image != nullptr; image = image->next) {
        if (image->w <= 0 || image->h <= 0) continue;
        if (first) {
            bbox.x = image->dst_x;
            bbox.y = image->dst_y;
            bbox.w = image->w;
            bbox.h = image->h;
            first = false;
        } else {
            const int old_right = bbox.x + bbox.w;
            const int old_bottom = bbox.y + bbox.h;
            bbox.x = std::min(bbox.x, image->dst_x);
            bbox.y = std::min(bbox.y, image->dst_y);
            bbox.w = std::max(old_right, image->dst_x + image->w) - bbox.x;
            bbox.h = std::max(old_bottom, image->dst_y + image->h) - bbox.y;
        }
    }
    return bbox;
}

void clear_buffer_region(
    ANativeWindow_Buffer* buffer,
    int x,
    int y,
    int w,
    int h) {
    if (buffer == nullptr || buffer->bits == nullptr || w <= 0 || h <= 0) return;
    const int left = std::max(0, x);
    const int top = std::max(0, y);
    const int right = std::min(buffer->width, x + w);
    const int bottom = std::min(buffer->height, y + h);
    if (left >= right || top >= bottom) return;
    auto* pixels = static_cast<uint8_t*>(buffer->bits);
    const size_t row_bytes = static_cast<size_t>(right - left) * sizeof(uint32_t);
    for (int row = top; row < bottom; ++row) {
        std::memset(
            pixels + static_cast<size_t>(row) * static_cast<size_t>(buffer->stride) * 4U +
                static_cast<size_t>(left) * sizeof(uint32_t),
            0,
            row_bytes);
    }
}

void clear_buffer_full(ANativeWindow_Buffer* buffer) {
    if (buffer == nullptr || buffer->bits == nullptr) return;
    std::memset(
        buffer->bits,
        0,
        static_cast<size_t>(buffer->stride) * static_cast<size_t>(buffer->height) * sizeof(uint32_t));
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
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0 || buffer.bits == nullptr) {
        LOGW("Could not lock subtitle surface");
        return false;
    }
    std::memset(
        buffer.bits,
        0,
        static_cast<size_t>(buffer.stride) * static_cast<size_t>(buffer.height) * sizeof(uint32_t));
    ANativeWindow_unlockAndPost(window);
    return true;
}

void blend_image(ANativeWindow_Buffer* buffer, const ASS_Image* image) {
    if (buffer == nullptr || image == nullptr || image->bitmap == nullptr) return;
    const int color_alpha = 255 - static_cast<int>(image->color & 0xffU);
    if (color_alpha <= 0) return;

    const int source_r = static_cast<int>((image->color >> 24U) & 0xffU);
    const int source_g = static_cast<int>((image->color >> 16U) & 0xffU);
    const int source_b = static_cast<int>((image->color >> 8U) & 0xffU);
    const int left = std::max(0, image->dst_x);
    const int top = std::max(0, image->dst_y);
    const int right = std::min(buffer->width, image->dst_x + image->w);
    const int bottom = std::min(buffer->height, image->dst_y + image->h);
    if (left >= right || top >= bottom) return;

    auto* pixels = static_cast<uint8_t*>(buffer->bits);
    for (int y = top; y < bottom; ++y) {
        const auto* source_row = image->bitmap + (y - image->dst_y) * image->stride;
        auto* target_row = pixels + static_cast<size_t>(y) * static_cast<size_t>(buffer->stride) * 4U;
        for (int x = left; x < right; ++x) {
            const int coverage = source_row[x - image->dst_x];
            if (coverage == 0) continue;
            const int source_alpha = (color_alpha * coverage + 127) / 255;
            auto* target = target_row + static_cast<size_t>(x) * 4U;
            target[0] = static_cast<uint8_t>(
                (source_r * source_alpha + target[0] * (255 - source_alpha) + 127) / 255);
            target[1] = static_cast<uint8_t>(
                (source_g * source_alpha + target[1] * (255 - source_alpha) + 127) / 255);
            target[2] = static_cast<uint8_t>(
                (source_b * source_alpha + target[2] * (255 - source_alpha) + 127) / 255);
            target[3] = static_cast<uint8_t>(std::min(
                255,
                source_alpha + (target[3] * (255 - source_alpha) + 127) / 255));
        }
    }
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

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) return -1;

    std::lock_guard<std::mutex> lock(session->mutex);
    if (session->renderer == nullptr || session->track == nullptr) {
        ANativeWindow_release(window);
        return -1;
    }

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

    const bool geometry_same =
        session->posted_width == frame_width && session->posted_height == frame_height;
    // Static subtitles: libass output is byte-identical, the surface already
    // shows it, and the geometry did not change. Skip all window traffic.
    if (changed == 0 && geometry_same && session->surface_has_content) {
        return 0;
    }

    if (!geometry_same &&
        ANativeWindow_setBuffersGeometry(
            window,
            frame_width,
            frame_height,
            WINDOW_FORMAT_RGBA_8888) != 0) {
        ANativeWindow_release(window);
        return -1;
    }

    ANativeWindow_Buffer buffer {};
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0 || buffer.bits == nullptr) {
        ANativeWindow_release(window);
        return -1;
    }

    const BBox new_bbox = images_bbox(images);
    if (!session->surface_has_content || !session->have_last_dirty) {
        clear_buffer_full(&buffer);
    } else {
        // Clear only the union of the previous and current image areas.
        const BBox& old = session->last_dirty;
        const int clear_x = std::min(old.x, new_bbox.x);
        const int clear_y = std::min(old.y, new_bbox.y);
        const int clear_right =
            std::max(old.x + old.w, new_bbox.x + new_bbox.w);
        const int clear_bottom =
            std::max(old.y + old.h, new_bbox.y + new_bbox.h);
        clear_buffer_region(&buffer, clear_x, clear_y, clear_right - clear_x, clear_bottom - clear_y);
    }
    for (ASS_Image* image = images; image != nullptr; image = image->next) {
        blend_image(&buffer, image);
    }
    ANativeWindow_unlockAndPost(window);
    ANativeWindow_release(window);

    session->surface_has_content = true;
    session->posted_width = frame_width;
    session->posted_height = frame_height;
    session->last_dirty = new_bbox;
    session->have_last_dirty = true;
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
        session->surface_has_content = false;
        session->posted_width = 0;
        session->posted_height = 0;
        session->have_last_dirty = false;
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
