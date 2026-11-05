// SPDX-License-Identifier: GPL-3.0-only
#include "../arc_present.h"
#include <stddef.h>
#include <stdio.h>

_Static_assert(sizeof(arc_present_frame) == 32, "Presentation frame ABI width");
_Static_assert(offsetof(arc_present_frame, frame_id) == 0, "frame offset");
_Static_assert(offsetof(arc_present_frame, width) == 8, "width offset");
_Static_assert(offsetof(arc_present_frame, height) == 12, "height offset");
_Static_assert(offsetof(arc_present_frame, source_framebuffer) == 16, "fbo offset");
_Static_assert(offsetof(arc_present_frame, source_read_buffer) == 20, "buffer offset");
_Static_assert(offsetof(arc_present_frame, color_encoding) == 24, "encoding offset");
_Static_assert(offsetof(arc_present_frame, flags) == 28, "flags offset");
#define CHECK(x) do { if (!(x)) { fprintf(stderr, "CHECK failed at %d: %s\n", __LINE__, #x); return 1; } } while (0)
int main(void) {
    uint64_t bytes = 0;
    arc_present_frame value = {1, 1920, 1080, 0, 0x0405, ARC_PRESENT_RGBA8_LINEAR, 0};
    CHECK(arc_present_valid_frame(&value, &bytes)); CHECK(bytes == UINT64_C(1920) * 1080 * 4);
    value.frame_id = 0; CHECK(!arc_present_valid_frame(&value, &bytes)); value.frame_id = 1;
    value.source_framebuffer = 2; CHECK(!arc_present_valid_frame(&value, &bytes)); value.source_framebuffer = 0;
    value.source_read_buffer = 0x0404; CHECK(!arc_present_valid_frame(&value, &bytes)); value.source_read_buffer = 0x0405;
    value.flags = 1; CHECK(!arc_present_valid_frame(&value, &bytes)); value.flags = 0;
    value.width = 0; CHECK(!arc_present_valid_frame(&value, &bytes)); value.width = 1920;
    value.color_encoding = 9; CHECK(!arc_present_valid_frame(&value, &bytes)); value.color_encoding = ARC_PRESENT_RGBA8_SRGB;
    CHECK(arc_present_valid_frame(&value, &bytes));
    value.width = 16384; value.height = 16384; CHECK(!arc_present_valid_frame(&value, &bytes));
    value.width = 8192; value.height = 8192; CHECK(!arc_present_valid_frame(&value, &bytes));
    CHECK(!arc_present_valid_frame(NULL, &bytes)); CHECK(!arc_present_valid_frame(&value, NULL));
    return 0;
}
