// SPDX-License-Identifier: GPL-3.0-only
#include "arc_present.h"

int arc_present_valid_frame(const arc_present_frame *frame, uint64_t *bytes) {
    if (!frame || !bytes || frame->frame_id == 0 || frame->flags != 0
        || frame->source_framebuffer != 0 || frame->source_read_buffer != 0x0405
        || frame->width < 1 || frame->height < 1 || frame->width > 16384 || frame->height > 16384
        || (frame->color_encoding != ARC_PRESENT_RGBA8_LINEAR && frame->color_encoding != ARC_PRESENT_RGBA8_SRGB))
        return 0;
    uint64_t required = (uint64_t)frame->width * frame->height * 4;
    if (required > UINT64_C(128) * 1024 * 1024) return 0;
    *bytes = required;
    return 1;
}
