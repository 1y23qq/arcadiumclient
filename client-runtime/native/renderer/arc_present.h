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
       ARC_PRESENT_IN_FLIGHT = 9, ARC_PRESENT_FALLBACKS = 10 };

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
ARC_API uint64_t arc_present_query(void *bridge, int key);

int arc_present_valid_frame(const arc_present_frame *frame, uint64_t *rgba_bytes);

#ifdef __cplusplus
}
#endif
#endif
