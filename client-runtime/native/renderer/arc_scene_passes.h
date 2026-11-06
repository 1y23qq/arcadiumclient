// SPDX-License-Identifier: GPL-3.0-only
#ifndef ARCADIUM_SCENE_PASSES_H
#define ARCADIUM_SCENE_PASSES_H
#include "arc_scene.h"

enum { ARC_PIPE_DEPTH_TEST = 1, ARC_PIPE_DEPTH_WRITE = 2, ARC_PIPE_BLEND = 4 };
enum { ARC_COMPARE_NEVER, ARC_COMPARE_LESS, ARC_COMPARE_EQUAL, ARC_COMPARE_LEQUAL,
       ARC_COMPARE_GREATER, ARC_COMPARE_NOTEQUAL, ARC_COMPARE_GEQUAL, ARC_COMPARE_ALWAYS };
enum { ARC_CULL_NONE, ARC_CULL_FRONT, ARC_CULL_BACK };
enum { ARC_WIND_CCW, ARC_WIND_CW };
enum { ARC_BLEND_ZERO, ARC_BLEND_ONE, ARC_BLEND_SRC_COLOR, ARC_BLEND_INV_SRC_COLOR,
       ARC_BLEND_DST_COLOR, ARC_BLEND_INV_DST_COLOR, ARC_BLEND_SRC_ALPHA,
       ARC_BLEND_INV_SRC_ALPHA, ARC_BLEND_DST_ALPHA, ARC_BLEND_INV_DST_ALPHA };
enum { ARC_BLEND_ADD, ARC_BLEND_SUBTRACT, ARC_BLEND_REVERSE_SUBTRACT, ARC_BLEND_MIN, ARC_BLEND_MAX };
enum { ARC_LOAD, ARC_CLEAR, ARC_DISCARD };
enum { ARC_ATTACH_COLOR = 1, ARC_ATTACH_DEPTH = 2, ARC_PASS_SCISSOR = 1 };
enum { ARC_PIPELINE_LIMIT = 64, ARC_PASS_LIMIT = 64, ARC_UNIFORM_ALIGNMENT = 256 };

typedef struct {
    uint32_t flags, depth_compare, cull, winding;
    uint32_t src_rgb, dst_rgb, src_alpha, dst_alpha;
    uint32_t rgb_operation, alpha_operation, color_mask, reserved;
} arc_scene_pipeline_state;

// Rectangles use the OpenGL lower-left convention. Clear actions ignore draw write masks.
typedef struct {
    uint32_t pipeline_id, first_command, command_count, uniform_offset;
    uint32_t uniform_bytes, load_actions, store_actions, flags;
    uint32_t viewport_x, viewport_y, viewport_width, viewport_height;
    uint32_t scissor_x, scissor_y, scissor_width, scissor_height;
    float clear_rgba[4];
    float clear_depth, min_depth, max_depth;
    uint32_t reserved;
} arc_scene_pass;

#ifdef __cplusplus
extern "C" {
#endif
ARC_API int arc_scene_pass_abi(void);
ARC_API int arc_scene_pipeline_state_put(void *context, int id, const char *source,
    const char *vertex, const char *fragment, const arc_scene_pipeline_state *state);
ARC_API int arc_scene_pipeline_state_remove(void *context, int id);
ARC_API int arc_scene_submit_passes(void *context, int width, int height, const void *uniforms,
    uint64_t uniform_length, const arc_scene_pass *passes, int pass_count,
    const arc_scene_command *commands, int command_count);
ARC_API int arc_scene_read_depth(void *context, void *destination, uint64_t length);

int arc_scene_valid_pipeline_state(const arc_scene_pipeline_state *state);
int arc_scene_valid_passes(int width, int height, uint64_t uniform_length, const arc_scene_pass *passes,
    int pass_count, int command_count, uint32_t previous_stored, uint32_t *stored);
#ifdef __cplusplus
}
#endif
#endif
