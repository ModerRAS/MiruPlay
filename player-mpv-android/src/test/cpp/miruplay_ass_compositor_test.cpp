#include "miruplay_ass_compositor.h"

#include <algorithm>
#include <array>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <vector>

namespace compositor = miruplay::ass_compositor;

namespace {

uint8_t* pixel(compositor::Buffer buffer, int x, int y) {
    return buffer.bits + (static_cast<size_t>(y) * buffer.stride + x) * compositor::kBytesPerPixel;
}

void assert_pixel(compositor::Buffer buffer, int x, int y, std::array<uint8_t, 4> expected) {
    const uint8_t* actual = pixel(buffer, x, y);
    for (size_t channel = 0; channel < expected.size(); ++channel) {
        assert(actual[channel] == expected[channel]);
    }
}

void transparent_clear_and_blend_respect_stride() {
    std::vector<uint8_t> storage(6 * 3 * 4, 0x7f);
    compositor::Buffer buffer{storage.data(), 4, 3, 6};
    compositor::clear(buffer, compositor::full_rect(buffer.width, buffer.height));

    for (int y = 0; y < buffer.height; ++y) {
        for (int x = 0; x < buffer.width; ++x) assert_pixel(buffer, x, y, {0, 0, 0, 0});
        for (int x = buffer.width; x < buffer.stride; ++x) {
            assert_pixel(buffer, x, y, {0x7f, 0x7f, 0x7f, 0x7f});
        }
    }

    const uint8_t coverage[] = {255};
    compositor::blend(buffer, {1, 1, 1, coverage, 0xff00007fU, 2, 1});
    assert_pixel(buffer, 2, 1, {128, 0, 0, 128});
    assert_pixel(buffer, 0, 0, {0, 0, 0, 0});
}

void moving_and_disappearing_subtitles_clear_old_pixels() {
    std::vector<uint8_t> storage(12 * 4 * 4, 0);
    compositor::Buffer buffer{storage.data(), 10, 4, 12};
    const uint8_t coverage[] = {255, 255};
    const compositor::Image old_image{2, 1, 2, coverage, 0x00ff0000U, 1, 1};
    const compositor::Image new_image{2, 1, 2, coverage, 0x00ff0000U, 6, 1};
    compositor::blend(buffer, old_image);
    assert_pixel(buffer, 1, 1, {0, 255, 0, 255});

    compositor::FrameState state{true, 10, 4, compositor::bounds(old_image, 10, 4)};
    const auto move = compositor::plan_frame(state, 1, 10, 4, compositor::bounds(new_image, 10, 4));
    assert(move.post && !move.configure_geometry);
    assert(move.dirty.left == 1 && move.dirty.top == 1 && move.dirty.right == 8 && move.dirty.bottom == 2);
    compositor::clear(buffer, move.dirty);
    compositor::blend(buffer, new_image);
    compositor::commit_frame(&state, 10, 4, compositor::bounds(new_image, 10, 4));
    assert_pixel(buffer, 1, 1, {0, 0, 0, 0});
    assert_pixel(buffer, 6, 1, {0, 255, 0, 255});

    const auto disappear = compositor::plan_frame(state, 1, 10, 4, {});
    assert(disappear.post && disappear.dirty.left == 6 && disappear.dirty.right == 8);
    compositor::clear(buffer, disappear.dirty);
    compositor::commit_frame(&state, 10, 4, {});
    assert_pixel(buffer, 6, 1, {0, 0, 0, 0});
}

void returned_dirty_expansion_is_fully_redrawn() {
    std::vector<uint8_t> storage(8 * 3 * 4, 0);
    compositor::Buffer buffer{storage.data(), 8, 3, 8};
    const uint8_t coverage[] = {255};
    const compositor::Image image{1, 1, 1, coverage, 0x0000ff00U, 5, 1};
    std::fill(pixel(buffer, 2, 1), pixel(buffer, 2, 1) + 4, 0xff);
    const compositor::Rect requested{4, 1, 6, 2};
    const compositor::Rect returned{2, 0, 7, 3};
    assert(compositor::contains(returned, requested));
    compositor::clear(buffer, returned);
    compositor::blend(buffer, image);
    assert_pixel(buffer, 2, 1, {0, 0, 0, 0});
    assert_pixel(buffer, 5, 1, {0, 0, 255, 255});
}

void unchanged_frames_do_not_repost_or_create_opaque_backgrounds() {
    compositor::FrameState state;
    const compositor::Rect subtitle{2, 2, 4, 3};
    const auto first = compositor::plan_frame(state, 1, 16, 9, subtitle);
    assert(first.post && first.configure_geometry);
    assert(first.dirty.left == 0 && first.dirty.top == 0 && first.dirty.right == 16 && first.dirty.bottom == 9);

    std::vector<uint8_t> storage(16 * 9 * 4, 0xff);
    compositor::Buffer buffer{storage.data(), 16, 9, 16};
    const uint8_t coverage[] = {255, 255};
    const compositor::Image image{2, 1, 2, coverage, 0xffffff00U, 2, 2};
    compositor::clear(buffer, first.dirty);
    compositor::blend(buffer, image);
    compositor::commit_frame(&state, 16, 9, subtitle);

    int posts = 1;
    int full_surface_posts = 1;
    for (int frame = 0; frame < 1000; ++frame) {
        const auto plan = compositor::plan_frame(state, 0, 16, 9, subtitle);
        posts += plan.post ? 1 : 0;
        full_surface_posts += plan.post && plan.dirty.left == 0 && plan.dirty.top == 0 &&
            plan.dirty.right == 16 && plan.dirty.bottom == 9 ? 1 : 0;
    }
    assert(posts == 1);
    assert(full_surface_posts == 1);
    assert_pixel(buffer, 0, 0, {0, 0, 0, 0});
    assert_pixel(buffer, 2, 2, {255, 255, 255, 255});

    int changed_posts = 0;
    int changed_full_surface_posts = 0;
    for (int frame = 0; frame < 1000; ++frame) {
        const auto plan = compositor::plan_frame(state, 1, 16, 9, subtitle);
        changed_posts += plan.post ? 1 : 0;
        changed_full_surface_posts += plan.post && plan.dirty.left == 0 && plan.dirty.top == 0 &&
            plan.dirty.right == 16 && plan.dirty.bottom == 9 ? 1 : 0;
    }
    assert(changed_posts == 1000);
    assert(changed_full_surface_posts == 0);
}

}  // namespace

int main() {
    transparent_clear_and_blend_respect_stride();
    moving_and_disappearing_subtitles_clear_old_pixels();
    returned_dirty_expansion_is_fully_redrawn();
    unchanged_frames_do_not_repost_or_create_opaque_backgrounds();
    std::cout << "miruplay_ass_compositor_test: PASS\n";
    return 0;
}
