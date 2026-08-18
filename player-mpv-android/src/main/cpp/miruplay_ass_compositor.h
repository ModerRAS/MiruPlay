#ifndef MIRUPLAY_ASS_COMPOSITOR_H
#define MIRUPLAY_ASS_COMPOSITOR_H

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace miruplay::ass_compositor {

constexpr int kBytesPerPixel = 4;

struct Rect {
    int left = 0;
    int top = 0;
    int right = 0;
    int bottom = 0;
};

inline bool empty(const Rect& rect) {
    return rect.left >= rect.right || rect.top >= rect.bottom;
}

inline Rect full_rect(int width, int height) {
    return width > 0 && height > 0 ? Rect{0, 0, width, height} : Rect{};
}

inline Rect rect_from_xywh(int x, int y, int width, int height, int limit_width, int limit_height) {
    if (width <= 0 || height <= 0 || limit_width <= 0 || limit_height <= 0) return {};
    const auto right = static_cast<int64_t>(x) + width;
    const auto bottom = static_cast<int64_t>(y) + height;
    return {
        std::max(0, x),
        std::max(0, y),
        static_cast<int>(std::min<int64_t>(limit_width, right)),
        static_cast<int>(std::min<int64_t>(limit_height, bottom)),
    };
}

inline Rect clamp_rect(const Rect& rect, int width, int height) {
    if (width <= 0 || height <= 0) return {};
    return {
        std::clamp(rect.left, 0, width),
        std::clamp(rect.top, 0, height),
        std::clamp(rect.right, 0, width),
        std::clamp(rect.bottom, 0, height),
    };
}

inline Rect unite(const Rect& first, const Rect& second) {
    if (empty(first)) return second;
    if (empty(second)) return first;
    return {
        std::min(first.left, second.left),
        std::min(first.top, second.top),
        std::max(first.right, second.right),
        std::max(first.bottom, second.bottom),
    };
}

inline bool contains(const Rect& outer, const Rect& inner) {
    return empty(inner) || (!empty(outer) && outer.left <= inner.left && outer.top <= inner.top &&
        outer.right >= inner.right && outer.bottom >= inner.bottom);
}

struct Buffer {
    uint8_t* bits = nullptr;
    int width = 0;
    int height = 0;
    int stride = 0;
};

inline bool valid(const Buffer& buffer) {
    return buffer.bits != nullptr && buffer.width > 0 && buffer.height > 0 &&
        buffer.stride >= buffer.width;
}

inline void clear(Buffer buffer, const Rect& requested) {
    if (!valid(buffer)) return;
    const Rect rect = clamp_rect(requested, buffer.width, buffer.height);
    if (empty(rect)) return;
    const size_t row_bytes = static_cast<size_t>(rect.right - rect.left) * kBytesPerPixel;
    for (int y = rect.top; y < rect.bottom; ++y) {
        std::memset(
            buffer.bits + static_cast<size_t>(y) * buffer.stride * kBytesPerPixel +
                static_cast<size_t>(rect.left) * kBytesPerPixel,
            0,
            row_bytes);
    }
}

struct Image {
    int width = 0;
    int height = 0;
    int stride = 0;
    const uint8_t* bitmap = nullptr;
    uint32_t color = 0;
    int x = 0;
    int y = 0;
};

inline Rect bounds(const Image& image, int width, int height) {
    return rect_from_xywh(image.x, image.y, image.width, image.height, width, height);
}

inline void blend(Buffer buffer, const Image& image) {
    if (!valid(buffer) || image.bitmap == nullptr || image.stride < image.width) return;
    const int color_alpha = 255 - static_cast<int>(image.color & 0xffU);
    if (color_alpha <= 0) return;
    const Rect target_rect = bounds(image, buffer.width, buffer.height);
    if (empty(target_rect)) return;

    const int source_r = static_cast<int>((image.color >> 24U) & 0xffU);
    const int source_g = static_cast<int>((image.color >> 16U) & 0xffU);
    const int source_b = static_cast<int>((image.color >> 8U) & 0xffU);
    for (int y = target_rect.top; y < target_rect.bottom; ++y) {
        const auto* source_row = image.bitmap + static_cast<size_t>(y - image.y) * image.stride;
        auto* target_row = buffer.bits + static_cast<size_t>(y) * buffer.stride * kBytesPerPixel;
        for (int x = target_rect.left; x < target_rect.right; ++x) {
            const int coverage = source_row[x - image.x];
            if (coverage == 0) continue;
            const int source_alpha = (color_alpha * coverage + 127) / 255;
            auto* target = target_row + static_cast<size_t>(x) * kBytesPerPixel;
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

struct FrameState {
    bool posted = false;
    int width = 0;
    int height = 0;
    Rect content{};
};

struct FramePlan {
    bool post = false;
    bool configure_geometry = false;
    Rect dirty{};
};

inline FramePlan plan_frame(
    const FrameState& state,
    int changed,
    int width,
    int height,
    const Rect& content) {
    if (width <= 0 || height <= 0) return {};
    const bool geometry_same = state.posted && state.width == width && state.height == height;
    if (geometry_same && changed == 0) return {};
    if (!geometry_same) return {true, true, full_rect(width, height)};

    const Rect dirty = unite(
        clamp_rect(state.content, width, height),
        clamp_rect(content, width, height));
    return {!empty(dirty), false, dirty};
}

inline void commit_frame(FrameState* state, int width, int height, const Rect& content) {
    if (state == nullptr) return;
    state->posted = true;
    state->width = width;
    state->height = height;
    state->content = clamp_rect(content, width, height);
}

}  // namespace miruplay::ass_compositor

#endif  // MIRUPLAY_ASS_COMPOSITOR_H
