// SPDX-License-Identifier: GPL-3.0-only
#include "arc_scene_passes.h"
#include <math.h>

static int alpha_factor(uint32_t value) {
    return value <= ARC_BLEND_ONE || (value >= ARC_BLEND_SRC_ALPHA && value <= ARC_BLEND_INV_DST_ALPHA);
}

int arc_scene_valid_pipeline_state(const arc_scene_pipeline_state *s) {
    return s && !((uintptr_t)s & 3) && !(s->flags & ~(uint32_t)7) && (!(s->flags & ARC_PIPE_DEPTH_WRITE) || (s->flags & ARC_PIPE_DEPTH_TEST))
        && s->depth_compare <= ARC_COMPARE_ALWAYS && s->cull <= ARC_CULL_BACK && s->winding <= ARC_WIND_CW
        && s->src_rgb <= ARC_BLEND_INV_DST_ALPHA && s->dst_rgb <= ARC_BLEND_INV_DST_ALPHA
        && alpha_factor(s->src_alpha) && alpha_factor(s->dst_alpha)
        && s->rgb_operation <= ARC_BLEND_MAX && s->alpha_operation <= ARC_BLEND_MAX
        && s->color_mask <= 15 && !s->reserved;
}

static int rectangle(uint32_t x, uint32_t y, uint32_t w, uint32_t h, int width, int height) {
    return w && h && x <= (uint32_t)width && w <= (uint32_t)width - x
        && y <= (uint32_t)height && h <= (uint32_t)height - y;
}

int arc_scene_valid_passes(int width, int height, uint64_t uniform_length, const arc_scene_pass *passes,
    int pass_count, int command_count, uint32_t previous_stored, uint32_t *stored) {
    uint64_t bytes;
    if (!stored || !arc_scene_rgba_bytes(width, height, &bytes) || !passes || ((uintptr_t)passes & 3) || pass_count < 1 || pass_count > ARC_PASS_LIMIT
        || command_count < 0 || command_count > 65536 || !uniform_length || uniform_length > 16384
        || (uniform_length & 3) || (previous_stored & ~(uint32_t)3)) return 0;
    uint32_t initialized = previous_stored, cursor = 0;
    for (int i = 0; i < pass_count; i++) {
        const arc_scene_pass *p = passes + i;
        uint32_t color_load = p->load_actions & 3, depth_load = (p->load_actions >> 2) & 3;
        if (p->pipeline_id < 1 || p->pipeline_id > ARC_PIPELINE_LIMIT || p->first_command != cursor
            || !arc_scene_valid_range(cursor, p->command_count, (uint64_t)command_count)
            || !p->uniform_bytes || (p->uniform_bytes & 3) || (p->uniform_offset & (ARC_UNIFORM_ALIGNMENT - 1))
            || !arc_scene_valid_range(p->uniform_offset, p->uniform_bytes, uniform_length)
            || (p->flags & ~(uint32_t)ARC_PASS_SCISSOR) || (p->load_actions & ~(uint32_t)15)
            || color_load > ARC_DISCARD || depth_load > ARC_DISCARD || (p->store_actions & ~(uint32_t)3) || p->reserved
            || !rectangle(p->viewport_x, p->viewport_y, p->viewport_width, p->viewport_height, width, height)
            || ((p->flags & ARC_PASS_SCISSOR) && !rectangle(p->scissor_x, p->scissor_y, p->scissor_width, p->scissor_height, width, height))
            || !isfinite(p->clear_depth) || p->clear_depth < 0 || p->clear_depth > 1
            || !isfinite(p->min_depth) || !isfinite(p->max_depth) || p->min_depth < 0 || p->max_depth > 1 || p->min_depth > p->max_depth)
            return 0;
        for (int c = 0; c < 4; c++) if (!isfinite(p->clear_rgba[c]) || p->clear_rgba[c] < 0 || p->clear_rgba[c] > 1) return 0;
        if ((color_load == ARC_LOAD && !(initialized & ARC_ATTACH_COLOR))
            || (depth_load == ARC_LOAD && !(initialized & ARC_ATTACH_DEPTH))) return 0;
        initialized = ((color_load != ARC_DISCARD ? ARC_ATTACH_COLOR : 0) | (depth_load != ARC_DISCARD ? ARC_ATTACH_DEPTH : 0)) & p->store_actions;
        cursor += p->command_count;
    }
    if (cursor != (uint32_t)command_count || !(initialized & ARC_ATTACH_COLOR)) return 0;
    *stored = initialized;
    return 1;
}
