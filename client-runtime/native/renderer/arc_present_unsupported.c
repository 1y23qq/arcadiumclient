// SPDX-License-Identifier: GPL-3.0-only
#include "arc_present.h"
#include <string.h>

static const char unavailable[] = "Current-frame Metal presentation is unavailable; keep OpenGL";
int arc_present_abi(void) { return 1; }
int arc_present_error(void *bridge, char *out, uint64_t capacity) {
    (void)bridge;
    if (!out || capacity == 0) return ARC_PRESENT_INVALID;
    uint64_t length = sizeof(unavailable) - 1;
    if (length >= capacity) length = capacity - 1;
    memcpy(out, unavailable, (size_t)length); out[length] = 0;
    return ARC_PRESENT_OK;
}
int arc_present_create(void *scene, void *window, void **out) {
    (void)scene; (void)window;
    if (!out) return ARC_PRESENT_INVALID;
    *out = NULL;
    return ARC_PRESENT_UNAVAILABLE;
}
int arc_present_set_swap_interval(void *bridge, int interval) {
    (void)bridge;
    return interval == 0 || interval == 1 ? ARC_PRESENT_UNAVAILABLE : ARC_PRESENT_INVALID;
}
int arc_present_prepare(void *bridge, const arc_present_frame *frame) {
    uint64_t bytes;
    (void)bridge;
    return arc_present_valid_frame(frame, &bytes) ? ARC_PRESENT_UNAVAILABLE : ARC_PRESENT_INVALID;
}
int arc_present_publish(void *bridge, uint64_t frame) { (void)bridge; (void)frame; return ARC_PRESENT_UNAVAILABLE; }
int arc_present_hide(void *bridge) { return bridge ? ARC_PRESENT_UNAVAILABLE : ARC_PRESENT_OK; }
int arc_present_release_gl(void *bridge) { return bridge ? ARC_PRESENT_UNAVAILABLE : ARC_PRESENT_OK; }
int arc_present_destroy(void *bridge) { return bridge ? ARC_PRESENT_UNAVAILABLE : ARC_PRESENT_OK; }
uint64_t arc_present_query(void *bridge, int key) { (void)bridge; (void)key; return 0; }
