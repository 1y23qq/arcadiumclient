// SPDX-License-Identifier: GPL-3.0-only
#include "arc_scene.h"

int arc_scene_valid_range(uint64_t offset, uint64_t length, uint64_t capacity) {
    return offset <= capacity && length <= capacity - offset;
}

int arc_scene_valid_layout(int capacity, int actor_stride, int vertex_stride, int uniform_bytes) {
    return capacity >= 1 && capacity <= 65536 && actor_stride >= 4 && actor_stride <= 4096
        && !(actor_stride & 3) && vertex_stride >= 4 && vertex_stride <= 1024 && !(vertex_stride & 3)
        && uniform_bytes >= 4 && uniform_bytes <= 16384 && !(uniform_bytes & 3)
        && (uint64_t) capacity * (uint64_t) actor_stride <= UINT64_C(256) * 1024 * 1024;
}

int arc_scene_rgba_bytes(int width, int height, uint64_t *bytes) {
    if (!bytes || width < 1 || height < 1 || width > 16384 || height > 16384) return 0;
    uint64_t size = (uint64_t) width * (uint64_t) height * 4;
    if (size > UINT64_C(64) * 1024 * 1024) return 0;
    *bytes = size;
    return 1;
}
