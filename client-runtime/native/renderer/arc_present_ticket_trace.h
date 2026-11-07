// SPDX-License-Identifier: GPL-3.0-only
#ifndef ARCADIUM_PRESENT_TICKET_TRACE_H
#define ARCADIUM_PRESENT_TICKET_TRACE_H
#include <stdint.h>
#include <stddef.h>
#include <stdatomic.h>

enum { ARC_TRACE_CAPACITY=8, ARC_TRACE_HEADER_WORDS=9, ARC_TRACE_ROW_WORDS=24,
       ARC_TRACE_WORDS=ARC_TRACE_HEADER_WORDS+ARC_TRACE_CAPACITY*ARC_TRACE_ROW_WORDS,
       ARC_TRACE_BYTES=ARC_TRACE_WORDS*8 };
enum { ARC_TRACE_FRAME,ARC_TRACE_STATE,ARC_TRACE_LAYER,ARC_TRACE_VIEW,ARC_TRACE_DRAWABLE,
       ARC_TRACE_DRAWABLE_OBJECT,ARC_TRACE_SOURCE_GENERATION,ARC_TRACE_SOURCE_KIND,ARC_TRACE_ARMED_TICKS,
       ARC_TRACE_COMPLETED_TICKS,ARC_TRACE_COMPLETED_STATUS,ARC_TRACE_COMPLETED_STATE,
       ARC_TRACE_PRESENTED_TICKS,ARC_TRACE_PRESENTED_TIME_BITS,ARC_TRACE_SHOWN_DRAWABLE,ARC_TRACE_SHOWN_OBJECT,
       ARC_TRACE_PRESENTED_STATE,ARC_TRACE_DISPLAYED_BEFORE,ARC_TRACE_DISPLAYED_AFTER,
       ARC_TRACE_COMPLETED_CALLS,ARC_TRACE_PRESENTED_CALLS,ARC_TRACE_SOURCE_IMAGE,ARC_TRACE_SUPERLAYER,
       ARC_TRACE_SNAPSHOT_FLAGS };
enum { ARC_TRACE_RACING=1, ARC_TRACE_DUPLICATE=2 };
#if defined(__APPLE__)
_Static_assert(ATOMIC_LLONG_LOCK_FREE==2,"Ticket trace requires lock-free 64-bit atomics");
#endif
typedef struct { _Atomic(uint64_t) words[ARC_TRACE_ROW_WORDS]; _Atomic(uint64_t) completed_sequence,presented_sequence; } arc_ticket_trace_row;
typedef struct {
    arc_ticket_trace_row rows[ARC_TRACE_CAPACITY];
    _Atomic(uint64_t) count,overflow,identity_errors;
    uint64_t numer,denom,state;
} arc_ticket_trace;
static inline void arc_ticket_trace_init(arc_ticket_trace *trace,uint64_t state,uint64_t numer,uint64_t denom) {
    trace->state=state;trace->numer=numer;trace->denom=denom;
    atomic_init(&trace->count,0);atomic_init(&trace->overflow,0);atomic_init(&trace->identity_errors,0);
    for(unsigned i=0;i<ARC_TRACE_CAPACITY;i++) {
        for(unsigned j=0;j<ARC_TRACE_ROW_WORDS;j++)atomic_init(&trace->rows[i].words[j],0);
        atomic_init(&trace->rows[i].completed_sequence,0);atomic_init(&trace->rows[i].presented_sequence,0);
    }
}
static inline int arc_ticket_trace_begin(arc_ticket_trace *trace,uint64_t frame,uint64_t layer,uint64_t superlayer,uint64_t view,
        uint64_t drawable,uint64_t drawable_object,uint64_t source_generation,uint64_t source_kind,uint64_t source_image,uint64_t ticks) {
    uint64_t count=atomic_load(&trace->count);
    if(!frame||!trace->state||!trace->numer||!trace->denom){atomic_fetch_add(&trace->identity_errors,1);return -1;}
    if(count>=ARC_TRACE_CAPACITY){atomic_fetch_add(&trace->overflow,1);return -1;}
    for(uint64_t i=0;i<count;i++)if(atomic_load(&trace->rows[i].words[ARC_TRACE_FRAME])==frame){atomic_fetch_add(&trace->identity_errors,1);return -1;}
    arc_ticket_trace_row *row=&trace->rows[count];
    atomic_store(&row->words[ARC_TRACE_STATE],trace->state);atomic_store(&row->words[ARC_TRACE_LAYER],layer);
    atomic_store(&row->words[ARC_TRACE_SUPERLAYER],superlayer);atomic_store(&row->words[ARC_TRACE_VIEW],view);
    atomic_store(&row->words[ARC_TRACE_DRAWABLE],drawable);atomic_store(&row->words[ARC_TRACE_DRAWABLE_OBJECT],drawable_object);
    atomic_store(&row->words[ARC_TRACE_SOURCE_GENERATION],source_generation);atomic_store(&row->words[ARC_TRACE_SOURCE_KIND],source_kind);
    atomic_store(&row->words[ARC_TRACE_SOURCE_IMAGE],source_image);atomic_store(&row->words[ARC_TRACE_ARMED_TICKS],ticks);
    atomic_store_explicit(&row->words[ARC_TRACE_FRAME],frame,memory_order_release);atomic_store_explicit(&trace->count,count+1,memory_order_release);return (int)count;
}
static inline arc_ticket_trace_row *arc_ticket_trace_entry(arc_ticket_trace *trace,int index,uint64_t frame,uint64_t state) {
    if(index<0)return NULL;
    if(index>=ARC_TRACE_CAPACITY||(uint64_t)index>=atomic_load(&trace->count)||state!=trace->state
            ||atomic_load(&trace->rows[index].words[ARC_TRACE_FRAME])!=frame){atomic_fetch_add(&trace->identity_errors,1);return NULL;}
    return &trace->rows[index];
}
static inline void arc_ticket_trace_completed(arc_ticket_trace *trace,int index,uint64_t frame,uint64_t state,uint64_t ticks,uint64_t status) {
    arc_ticket_trace_row *row=arc_ticket_trace_entry(trace,index,frame,state);if(!row)return;uint64_t expected=0;
    if(!atomic_compare_exchange_strong(&row->completed_sequence,&expected,1)){atomic_fetch_add(&row->words[ARC_TRACE_COMPLETED_CALLS],1);atomic_fetch_add(&trace->identity_errors,1);return;}
    atomic_store(&row->words[ARC_TRACE_COMPLETED_TICKS],ticks);atomic_store(&row->words[ARC_TRACE_COMPLETED_STATUS],status);
    atomic_store(&row->words[ARC_TRACE_COMPLETED_STATE],state);atomic_store(&row->words[ARC_TRACE_COMPLETED_CALLS],1);
    atomic_store_explicit(&row->completed_sequence,2,memory_order_release);
}
static inline void arc_ticket_trace_presented(arc_ticket_trace *trace,int index,uint64_t frame,uint64_t state,uint64_t ticks,
        uint64_t shown_drawable,uint64_t shown_object,uint64_t presented_time_bits,uint64_t before,uint64_t after) {
    arc_ticket_trace_row *row=arc_ticket_trace_entry(trace,index,frame,state);if(!row)return;uint64_t expected=0;
    if(!atomic_compare_exchange_strong(&row->presented_sequence,&expected,1)){atomic_fetch_add(&row->words[ARC_TRACE_PRESENTED_CALLS],1);atomic_fetch_add(&trace->identity_errors,1);return;}
    atomic_store(&row->words[ARC_TRACE_PRESENTED_TICKS],ticks);atomic_store(&row->words[ARC_TRACE_PRESENTED_TIME_BITS],presented_time_bits);
    atomic_store(&row->words[ARC_TRACE_SHOWN_DRAWABLE],shown_drawable);atomic_store(&row->words[ARC_TRACE_SHOWN_OBJECT],shown_object);
    atomic_store(&row->words[ARC_TRACE_PRESENTED_STATE],state);atomic_store(&row->words[ARC_TRACE_DISPLAYED_BEFORE],before);atomic_store(&row->words[ARC_TRACE_DISPLAYED_AFTER],after);
    atomic_store(&row->words[ARC_TRACE_PRESENTED_CALLS],1);atomic_store_explicit(&row->presented_sequence,2,memory_order_release);
}
static inline int arc_ticket_trace_dump(const arc_ticket_trace *trace,uint64_t ticks,void *destination,uint64_t bytes) {
    if(!trace||!destination||bytes!=ARC_TRACE_BYTES||(uintptr_t)destination%8)return -1;
    uint64_t *out=destination;uint64_t count=atomic_load_explicit(&trace->count,memory_order_acquire);
    if(count>ARC_TRACE_CAPACITY)return -1;
    out[0]=1;out[1]=ARC_TRACE_CAPACITY;out[2]=count;out[3]=atomic_load(&trace->overflow);out[4]=atomic_load(&trace->identity_errors);
    out[5]=trace->numer;out[6]=trace->denom;out[7]=ticks;out[8]=trace->state;
    for(unsigned i=0;i<ARC_TRACE_CAPACITY;i++) {
        const arc_ticket_trace_row *row=&trace->rows[i];uint64_t c=atomic_load_explicit(&row->completed_sequence,memory_order_acquire),p=atomic_load_explicit(&row->presented_sequence,memory_order_acquire);
        for(unsigned j=0;j<ARC_TRACE_ROW_WORDS;j++)out[ARC_TRACE_HEADER_WORDS+i*ARC_TRACE_ROW_WORDS+j]=atomic_load(&row->words[j]);
        uint64_t c2=atomic_load_explicit(&row->completed_sequence,memory_order_acquire),p2=atomic_load_explicit(&row->presented_sequence,memory_order_acquire),flags=0;
        if(((c|p|c2|p2)&1)!=0||c!=c2||p!=p2)flags|=ARC_TRACE_RACING;
        if(out[ARC_TRACE_HEADER_WORDS+i*ARC_TRACE_ROW_WORDS+ARC_TRACE_COMPLETED_CALLS]>1||out[ARC_TRACE_HEADER_WORDS+i*ARC_TRACE_ROW_WORDS+ARC_TRACE_PRESENTED_CALLS]>1)flags|=ARC_TRACE_DUPLICATE;
        out[ARC_TRACE_HEADER_WORDS+i*ARC_TRACE_ROW_WORDS+ARC_TRACE_SNAPSHOT_FLAGS]=flags;
    }
    return 0;
}
#endif
