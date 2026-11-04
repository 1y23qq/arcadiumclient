// SPDX-License-Identifier: GPL-3.0-only
#include "../arc_scene.h"
#undef NDEBUG
#include <assert.h>
#include <stddef.h>
#include <stdio.h>

int main(void) {
    assert(sizeof(arc_scene_command) == 24);
    assert(offsetof(arc_scene_command, first_actor) == 16);
    assert(arc_scene_valid_layout(8192, 128, 32, 64));
    assert(!arc_scene_valid_layout(65537, 128, 32, 64));
    assert(!arc_scene_valid_layout(8192, 127, 32, 64));
    assert(!arc_scene_valid_layout(8192, 128, 1028, 64));
    assert(!arc_scene_valid_layout(8192, 128, 32, 16388));
    assert(arc_scene_valid_range(UINT64_MAX, 0, UINT64_MAX));
    assert(!arc_scene_valid_range(UINT64_MAX, 1, UINT64_MAX));
    assert(!arc_scene_valid_range(1, UINT64_MAX, UINT64_MAX));
    assert(arc_scene_valid_range(128, 128, 256));
    assert(!arc_scene_valid_range(129, 128, 256));
    uint64_t bytes = 0;
    assert(arc_scene_rgba_bytes(1920, 1080, &bytes) && bytes == UINT64_C(8294400));
    assert(!arc_scene_rgba_bytes(0, 1080, &bytes));
    assert(!arc_scene_rgba_bytes(16384, 16384, &bytes));
    assert(!arc_scene_rgba_bytes(1, 1, NULL));
    puts("Resident scene ABI ranges, allocation limits and command layout passed");
    return 0;
}
