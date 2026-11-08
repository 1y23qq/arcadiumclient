// SPDX-License-Identifier: GPL-3.0-only
#ifndef ARCADIUM_PRESENTED_TICKET_H
#define ARCADIUM_PRESENTED_TICKET_H
#include <stdint.h>
#include <math.h>
#include <stdatomic.h>

typedef struct {
    uint64_t frame, state, drawable, object;
} arc_presented_ticket;

static inline int arc_presented_ticket_matches(const arc_presented_ticket *ticket, uint64_t state,
        uint64_t callback_frame, uint64_t drawable, uint64_t object, double presented_time) {
    return ticket && ticket->frame && ticket->state && ticket->object && isfinite(presented_time) && presented_time > 0.0
        && callback_frame == ticket->frame && state == ticket->state
        && drawable == ticket->drawable && object == ticket->object;
}

static inline int arc_presented_ticket_accept(const arc_presented_ticket *ticket, atomic_uint_fast64_t *active_frame,
        atomic_uint_fast64_t *displayed, uint64_t state, uint64_t callback_frame, uint64_t drawable,
        uint64_t object, double presented_time) {
    if (!active_frame || !displayed || active_frame == displayed
            || !arc_presented_ticket_matches(ticket, state, callback_frame, drawable, object, presented_time)
            || atomic_load(displayed) >= ticket->frame) return 0;
    uint_fast64_t expected = ticket->frame;
    if (!atomic_compare_exchange_strong(active_frame, &expected, 0)) return 0;
    uint_fast64_t previous = atomic_load(displayed);
    while (previous < ticket->frame && !atomic_compare_exchange_weak(displayed, &previous, ticket->frame)) { }
    return 1;
}
#endif
