// SPDX-License-Identifier: GPL-3.0-only
#include "../arc_scene_passes.h"
#undef NDEBUG
#include <assert.h>
#include <math.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>

int main(void) {
    assert(sizeof(arc_scene_pipeline_state) == 48 && offsetof(arc_scene_pipeline_state, color_mask) == 40);
    assert(sizeof(arc_scene_pass) == 96 && offsetof(arc_scene_pass, clear_rgba) == 64 && offsetof(arc_scene_pass, clear_depth) == 80);
    arc_scene_pipeline_state state = {ARC_PIPE_DEPTH_TEST | ARC_PIPE_DEPTH_WRITE, ARC_COMPARE_LEQUAL, ARC_CULL_NONE,
        ARC_WIND_CCW, ARC_BLEND_ONE, ARC_BLEND_ZERO, ARC_BLEND_ONE, ARC_BLEND_ZERO, ARC_BLEND_ADD, ARC_BLEND_ADD, 15, 0};
    assert(arc_scene_valid_pipeline_state(&state));
    state.flags = ARC_PIPE_DEPTH_WRITE; assert(!arc_scene_valid_pipeline_state(&state));
    state.flags = ARC_PIPE_BLEND; state.src_alpha = ARC_BLEND_SRC_COLOR; assert(!arc_scene_valid_pipeline_state(&state));
    state.src_alpha = ARC_BLEND_ONE; state.reserved = 1; assert(!arc_scene_valid_pipeline_state(&state));
    state.reserved = 0; state.flags = 8; assert(!arc_scene_valid_pipeline_state(&state));
    arc_scene_pass passes[2] = {
        {1, 0, 1, 0, 80, ARC_CLEAR | ARC_CLEAR << 2, 3, 0, 0, 0, 64, 48, 0, 0, 0, 0, {0, 0, 0, 1}, 1, 0, 1, 0},
        {2, 1, 1, 256, 80, ARC_LOAD | ARC_LOAD << 2, 3, ARC_PASS_SCISSOR, 0, 0, 64, 48, 2, 3, 20, 30, {0, 0, 0, 1}, 1, 0, 1, 0}
    };
    uint32_t stored = 99;
    assert(arc_scene_valid_passes(64, 48, 512, passes, 2, 2, 0, &stored) && stored == 3);
    union { uint32_t alignment; uint8_t bytes[sizeof(passes)+4]; } unaligned;
    memcpy(unaligned.bytes+1, passes, sizeof(passes));stored=99;
    assert(!arc_scene_valid_passes(64,48,512,(const arc_scene_pass *)(unaligned.bytes+1),2,2,0,&stored)&&stored==99);
    passes[0].load_actions = 0; stored = 99;
    assert(!arc_scene_valid_passes(64, 48, 512, passes, 2, 2, 0, &stored) && stored == 99);
    assert(arc_scene_valid_passes(64, 48, 512, passes, 2, 2, 3, &stored));
    passes[0].load_actions = ARC_CLEAR | ARC_CLEAR << 2;
    passes[1].uniform_offset = 4; assert(!arc_scene_valid_passes(64, 48, 512, passes, 2, 2, 0, &stored));
    passes[1].uniform_offset = 256; passes[1].scissor_width = UINT32_MAX;
    assert(!arc_scene_valid_passes(64, 48, 512, passes, 2, 2, 0, &stored));
    passes[1].scissor_width = 20; passes[1].first_command = 0;
    assert(!arc_scene_valid_passes(64, 48, 512, passes, 2, 2, 0, &stored));
    passes[1].first_command = 1; passes[1].clear_rgba[2] = NAN;
    assert(!arc_scene_valid_passes(64, 48, 512, passes, 2, 2, 0, &stored));
    passes[1].clear_rgba[2] = 0; passes[0].store_actions = ARC_ATTACH_COLOR;
    assert(!arc_scene_valid_passes(64, 48, 512, passes, 2, 2, 0, &stored));
    passes[0].store_actions = 3; passes[1].store_actions = ARC_ATTACH_DEPTH;
    assert(!arc_scene_valid_passes(64, 48, 512, passes, 2, 2, 0, &stored));
    puts("Ordered pass ABI, attachment lifetime, uniform alignment, overflow and no-output-on-failure passed");
    return 0;
}
