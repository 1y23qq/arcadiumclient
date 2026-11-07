// SPDX-License-Identifier: GPL-3.0-only
#define ARC_PRESENT_TESTING 1
#include "../arc_present_macos.m"

ARC_API int arc_present_testing_read_rgba(void *value, uint64_t frame, void *destination, uint64_t length) {
    ArcPresentation *p=bridge(value);
    if(!p||!destination||!renderContext(p)||p.prepared!=frame||p.submitted!=frame
        ||atomic_load(&p.state->completed)!=frame||atomic_load(&p.state->failed)||!p.testingPixels
        ||length!=(uint64_t)p.image.width*p.image.height*4||p.testingStride<(NSUInteger)p.image.width*4
        ||p.testingPixels.length<p.testingStride*p.image.height)return ARC_PRESENT_INVALID;
    const uint8_t *source=p.testingPixels.contents;uint8_t *out=destination;
    for(NSUInteger y=0;y<p.image.height;y++)for(NSUInteger x=0;x<p.image.width;x++) {
        const uint8_t *pixel=source+y*p.testingStride+x*4;uint8_t *target=out+(y*p.image.width+x)*4;
        target[0]=pixel[2];target[1]=pixel[1];target[2]=pixel[0];target[3]=pixel[3];
    }
    return ARC_PRESENT_OK;
}

ARC_API int arc_present_testing_ticket_dump(void *value,void *destination,uint64_t length) {
    ArcPresentation *p=bridge(value);
    if(!p||!mainThread()||!renderContext(p))return ARC_PRESENT_INVALID;
    return arc_ticket_trace_dump(&p.state->ticketTrace,mach_absolute_time(),destination,length)==0?ARC_PRESENT_OK:ARC_PRESENT_INVALID;
}
