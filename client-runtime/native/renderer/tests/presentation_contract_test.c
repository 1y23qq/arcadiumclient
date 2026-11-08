// SPDX-License-Identifier: GPL-3.0-only
#include "../arc_present.h"
#include "../arc_presented_ticket.h"
#include <stddef.h>
#include <stdio.h>
#include <float.h>

_Static_assert(sizeof(arc_present_frame) == 32, "Presentation frame ABI width");
_Static_assert(offsetof(arc_present_frame, frame_id) == 0, "frame offset");
_Static_assert(offsetof(arc_present_frame, width) == 8, "width offset");
_Static_assert(offsetof(arc_present_frame, height) == 12, "height offset");
_Static_assert(offsetof(arc_present_frame, source_framebuffer) == 16, "fbo offset");
_Static_assert(offsetof(arc_present_frame, source_read_buffer) == 20, "buffer offset");
_Static_assert(offsetof(arc_present_frame, color_encoding) == 24, "encoding offset");
_Static_assert(offsetof(arc_present_frame, flags) == 28, "flags offset");
#define CHECK(x) do { if (!(x)) { fprintf(stderr, "CHECK failed at %d: %s\n", __LINE__, #x); return 1; } } while (0)
#define CASE(x) do { CHECK(x); cases++; } while (0)

static int displayed_contracts(void) {
    unsigned cases = 0;
    const arc_presented_ticket ticket = {7, 11, 0, 13};
    atomic_uint_fast64_t active, displayed, completed;
    atomic_init(&active, 7); atomic_init(&displayed, 5); atomic_init(&completed, 7);
    CASE(arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 0, 13, 1.25)
        && atomic_load(&active) == 0 && atomic_load(&displayed) == 7 && atomic_load(&completed) == 7);
    CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 0, 13, 1.25)
        && atomic_load(&active) == 0 && atomic_load(&displayed) == 7);
    const double positives[] = {DBL_MIN, DBL_MAX};
    for (unsigned i = 0; i < sizeof(positives) / sizeof(positives[0]); i++) {
        atomic_store(&active, 7); atomic_store(&displayed, 5);
        CASE(arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 0, 13, positives[i])
            && atomic_load(&active) == 0 && atomic_load(&displayed) == 7);
    }
    const double rejected[] = {0.0, -0.0, -1.0, NAN, INFINITY, -INFINITY};
    for (unsigned i = 0; i < sizeof(rejected) / sizeof(rejected[0]); i++) {
        atomic_store(&active, 7); atomic_store(&displayed, 5);
        CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 0, 13, rejected[i])
            && atomic_load(&active) == 7 && atomic_load(&displayed) == 5 && atomic_load(&completed) == 7);
    }
    CASE(!arc_presented_ticket_accept(NULL, &active, &displayed, 11, 7, 0, 13, 1.25));
    CASE(!arc_presented_ticket_accept(&ticket, NULL, &displayed, 11, 7, 0, 13, 1.25));
    CASE(!arc_presented_ticket_accept(&ticket, &active, NULL, 11, 7, 0, 13, 1.25));
    CASE(!arc_presented_ticket_accept(&ticket, &active, &active, 11, 7, 0, 13, 1.25));
    CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 12, 7, 0, 13, 1.25));
    CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 8, 0, 13, 1.25));
    CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 1, 13, 1.25));
    CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 0, 14, 1.25));
    CASE(atomic_load(&active) == 7 && atomic_load(&displayed) == 5 && atomic_load(&completed) == 7);
    arc_presented_ticket invalid = ticket; invalid.frame = 0;
    atomic_store(&active, 0);
    CASE(!arc_presented_ticket_accept(&invalid, &active, &displayed, 11, 0, 0, 13, 1.25));
    atomic_store(&active, 7); invalid = ticket; invalid.state = 0;
    CASE(!arc_presented_ticket_accept(&invalid, &active, &displayed, 0, 7, 0, 13, 1.25));
    invalid = ticket; invalid.object = 0;
    CASE(!arc_presented_ticket_accept(&invalid, &active, &displayed, 11, 7, 0, 0, 1.25));
    atomic_store(&active, 0);
    CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 0, 13, 1.25)
        && atomic_load(&displayed) == 5 && atomic_load(&completed) == 7);
    const arc_presented_ticket newer = {8, 11, 1, 13};
    atomic_store(&active, 8);
    CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 0, 13, 1.25) && atomic_load(&active) == 8);
    CASE(arc_presented_ticket_accept(&newer, &active, &displayed, 11, 8, 1, 13, 1.5)
        && atomic_load(&active) == 0 && atomic_load(&displayed) == 8 && atomic_load(&completed) == 7);
    CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 0, 13, 1.25) && atomic_load(&displayed) == 8);
    CASE(!arc_presented_ticket_accept(&newer, &active, &displayed, 11, 8, 1, 13, 1.5) && atomic_load(&displayed) == 8);
    atomic_store(&active, 7);
    CASE(!arc_presented_ticket_accept(&ticket, &active, &displayed, 11, 7, 0, 13, 1.25) && atomic_load(&displayed) == 8);
    const arc_presented_ticket maximum = {UINT64_MAX, 11, 2, 13};
    atomic_store(&active, UINT64_MAX);
    CASE(arc_presented_ticket_accept(&maximum, &active, &displayed, 11, UINT64_MAX, 2, 13, 2.0)
        && atomic_load(&active) == 0 && atomic_load(&displayed) == UINT64_MAX);
    CASE(!arc_presented_ticket_accept(&maximum, &active, &displayed, 11, UINT64_MAX, 2, 13, 2.0));
    printf("Displayed ticket atomic contracts: %u passed; shared production CAS/update helper\n", cases);
    return 0;
}

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
    CHECK(displayed_contracts() == 0);
    return 0;
}
