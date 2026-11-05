// SPDX-License-Identifier: GPL-3.0-only
#include "../arc_present.h"
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr, "CHECK failed at %d: %s\n", __LINE__, #x); return 1; } } while (0)
int main(void) {
    CHECK(arc_present_abi() == 1);
    void *value = (void *)(uintptr_t)9;
    CHECK(arc_present_create((void *)(uintptr_t)1, (void *)(uintptr_t)2, &value) == ARC_PRESENT_UNAVAILABLE);
    CHECK(value == NULL);
    CHECK(arc_present_create(NULL, NULL, NULL) == ARC_PRESENT_INVALID);
    char message[8]; memset(message, 0x7f, sizeof(message));
    CHECK(arc_present_error(NULL, message, 4) == ARC_PRESENT_OK);
    CHECK(message[3] == 0 && message[4] == 0x7f);
    CHECK(arc_present_error(NULL, message, 0) == ARC_PRESENT_INVALID);
    CHECK(arc_present_error(NULL, NULL, 9) == ARC_PRESENT_INVALID);
    CHECK(arc_present_hide(NULL) == ARC_PRESENT_OK);
    CHECK(arc_present_release_gl(NULL) == ARC_PRESENT_OK);
    CHECK(arc_present_destroy(NULL) == ARC_PRESENT_OK);
    CHECK(arc_present_query(NULL, ARC_PRESENT_ATTACHED) == 0);
    CHECK(arc_present_query(NULL, ARC_PRESENT_LIVE_BYTES) == 0);
    arc_present_frame frame = {1, 1920, 1080, 0, 0x0405, ARC_PRESENT_RGBA8_LINEAR, 0};
    CHECK(arc_present_prepare(NULL, &frame) == ARC_PRESENT_UNAVAILABLE);
    frame.flags = 1; CHECK(arc_present_prepare(NULL, &frame) == ARC_PRESENT_INVALID);
    CHECK(arc_present_publish(NULL, 1) == ARC_PRESENT_UNAVAILABLE);
    return 0;
}
