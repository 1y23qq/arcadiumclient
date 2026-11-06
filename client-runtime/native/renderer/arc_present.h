// SPDX-License-Identifier: GPL-3.0-only
#ifndef ARCADIUM_FRAME_PRESENTATION_H
#define ARCADIUM_FRAME_PRESENTATION_H
#include <stdint.h>
#include "arc_scene.h"
#ifdef __cplusplus
extern "C" {
#endif

enum { ARC_PRESENT_OK = 0, ARC_PRESENT_SKIP = 1, ARC_PRESENT_BUSY = 2,
       ARC_PRESENT_INVALID = -1, ARC_PRESENT_UNAVAILABLE = -2, ARC_PRESENT_FAILED = -3 };
enum { ARC_PRESENT_RGBA8_LINEAR = 1, ARC_PRESENT_RGBA8_SRGB = 2 };
enum { ARC_PRESENT_LAST_PREPARED = 1, ARC_PRESENT_LAST_SUBMITTED = 2,
       ARC_PRESENT_LAST_COMPLETED = 3, ARC_PRESENT_LAST_DISPLAYED = 4,
       ARC_PRESENT_LIVE_BYTES = 5, ARC_PRESENT_ATTACHED = 6,
       ARC_PRESENT_HIDDEN = 7, ARC_PRESENT_GL_RELEASED = 8,
       ARC_PRESENT_IN_FLIGHT = 9, ARC_PRESENT_FALLBACKS = 10,
       ARC_PRESENT_PARENT_WIDTH_FP64 = 11, ARC_PRESENT_PARENT_HEIGHT_FP64 = 12,
       ARC_PRESENT_VIEW_WIDTH_FP64 = 13, ARC_PRESENT_VIEW_HEIGHT_FP64 = 14,
       ARC_PRESENT_VIEW_FRAME_WIDTH_FP64 = 15, ARC_PRESENT_VIEW_FRAME_HEIGHT_FP64 = 16,
       ARC_PRESENT_DRAWABLE_WIDTH = 17, ARC_PRESENT_DRAWABLE_HEIGHT = 18,
       ARC_PRESENT_SCALE_FP64 = 19, ARC_PRESENT_WINDOW_SCALE_FP64 = 20,
       ARC_PRESENT_PARENT_ATTACHED = 21, ARC_PRESENT_WINDOW_VISIBLE = 22,
       ARC_PRESENT_WINDOW_OCCLUSION_VISIBLE = 23,
       ARC_PRESENT_WITH_TRANSACTION = 24,
       ARC_PRESENT_CHILD_WINDOW_MATCH = 25, ARC_PRESENT_PARENT_VIEW_ID = 26,
       ARC_PRESENT_CHILD_SUPERVIEW_ID = 27, ARC_PRESENT_CHILD_WINDOW_ID = 28,
       ARC_PRESENT_PARENT_LAYER_ID = 29, ARC_PRESENT_CHILD_LAYER_ID = 30,
       ARC_PRESENT_CHILD_SUPERLAYER_ID = 31, ARC_PRESENT_CHILD_LAYER_INDEX = 32,
       ARC_PRESENT_PARENT_LAYER_CHILDREN = 33, ARC_PRESENT_HIDDEN_ANCESTOR = 34,
       ARC_PRESENT_CHILD_LAYER_HIDDEN = 35, ARC_PRESENT_CHILD_LAYER_OPACITY_FP64 = 36,
       ARC_PRESENT_PARENT_LAYER_HIDDEN = 37, ARC_PRESENT_PARENT_LAYER_OPACITY_FP64 = 38,
       ARC_PRESENT_LAYER_CONTENTS_PRESENT = 39, ARC_PRESENT_VIEW_WANTS_UPDATE_LAYER = 40,
       ARC_PRESENT_VIEW_NEEDS_DISPLAY = 41, ARC_PRESENT_LAYER_DELEGATE_ID = 42,
       ARC_PRESENT_PRESENTATION_LAYER_ID = 43, ARC_PRESENT_PREPARED_CGL_SWAP_INTERVAL = 44,
       ARC_PRESENT_LAYER_DISPLAY_SYNC_ENABLED = 45, ARC_PRESENT_VIEW_VISIBLE_WIDTH_FP64 = 46,
       ARC_PRESENT_VIEW_VISIBLE_HEIGHT_FP64 = 47, ARC_PRESENT_LAYER_NEEDS_DISPLAY = 48,
       ARC_PRESENT_VIEW_OPAQUE = 49, ARC_PRESENT_CURRENT_GL_VIEW_LAYER_ID = 50,
       ARC_PRESENT_CURRENT_GL_VIEW_ID = 51, ARC_PRESENT_CURRENT_GL_CONTEXT_MATCH = 52,
       ARC_PRESENT_CONFIGURED_SWAP_INTERVAL = 53, ARC_PRESENT_PREPARED_SWAP_INTERVAL = 54,
       ARC_PRESENT_ORIGINAL_GL_SURFACE_ORDER = 55, ARC_PRESENT_PARENT_LAYER_Z_FP64 = 56,
       ARC_PRESENT_CHILD_LAYER_Z_FP64 = 57,
       ARC_PRESENT_PUBLISHED_LAYER_ID = 58, ARC_PRESENT_PUBLISHED_SUPERLAYER_ID = 59,
       ARC_PRESENT_PUBLISHED_DRAWABLE_LAYER_ID = 60, ARC_PRESENT_PUBLISHED_VIEW_ID = 61,
       ARC_PRESENT_PUBLISHED_CONTENT_VIEW_ID = 62, ARC_PRESENT_PUBLISHED_DRAWABLE_ID = 63,
       ARC_PRESENT_LAYER_CHAIN_BASE = 64, ARC_PRESENT_LAYER_CHAIN_END = 72,
       ARC_PRESENT_HOSTED_OWNERSHIP = 72,
       ARC_PRESENT_SIBLING_BASE = 128, ARC_PRESENT_SIBLING_STRIDE = 12, ARC_PRESENT_SIBLING_END = 176 };

typedef struct {
    uint64_t frame_id;
    uint32_t width, height;
    uint32_t source_framebuffer, source_read_buffer;
    uint32_t color_encoding, flags;
} arc_present_frame;

ARC_API int arc_present_abi(void);
ARC_API int arc_present_error(void *bridge, char *utf8, uint64_t capacity);
// Failure leaves out NULL, no child view and no retained borrowed window.
// Main thread; scene is alive; Cocoa window is borrowed, never SDL_Window*.
ARC_API int arc_present_create(void *scene, void *cocoa_window, void **bridge_out);
// Main thread; configures subsequently prepared frames. Only intervals 0 and 1 are supported.
ARC_API int arc_present_set_swap_interval(void *bridge, int interval);
// Render thread with the original CGL context current. Current final backbuffer only.
ARC_API int arc_present_prepare(void *bridge, const arc_present_frame *frame);
// Main thread; publishes the exact prepared frame ID; failures leave the layer hidden.
ARC_API int arc_present_publish(void *bridge, uint64_t frame_id);
// Main thread; idempotent. Required before same-frame GL swap after prepare failure.
ARC_API int arc_present_hide(void *bridge);
// Render thread with original CGL context current; deletes GL names, retires owned images.
ARC_API int arc_present_release_gl(void *bridge);
// Main thread, after successful release_gl, before parent SDL destruction/backend close.
ARC_API int arc_present_destroy(void *bridge);
// View queries11..71,128..175 require the Cocoa main thread; IDs are process-local.
ARC_API uint64_t arc_present_query(void *bridge, int key);

int arc_present_valid_frame(const arc_present_frame *frame, uint64_t *rgba_bytes);

#ifdef __cplusplus
}
#endif
#endif
