// SPDX-License-Identifier: GPL-3.0-only
#include "arc_present_scene.h"
int arc_present_valid_scene_frame(const arc_present_frame *f, const arc_scene_metal_info *s,
    uint64_t previous, uint64_t device, uint64_t queue, uint64_t *bytes) {
    if (!f || !s || !bytes || !device || !queue || !f->frame_id || f->frame_id <= previous
        || f->frame_id != s->frame_id || !s->generation || !s->texture || s->device != device || s->queue != queue
        || f->source_framebuffer || f->source_read_buffer || f->flags || f->color_encoding != ARC_PRESENT_RGBA8_LINEAR
        || f->width < 1 || f->height < 1 || f->width > 16384 || f->height > 16384
        || f->width != s->width || f->height != s->height) return 0;
    uint64_t required = (uint64_t)f->width * f->height * 4;
    if (required > UINT64_C(64) * 1024 * 1024) return 0;
    *bytes = required; return 1;
}
