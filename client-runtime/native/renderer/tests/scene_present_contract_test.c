// SPDX-License-Identifier: GPL-3.0-only
#include "../arc_present_scene.h"
#undef NDEBUG
#include <assert.h>
#include <stdio.h>
int main(void) {
    assert(sizeof(arc_present_frame)==32 && sizeof(arc_scene_metal_info)==48);
    arc_present_frame frame={7,192,96,0,0,ARC_PRESENT_RGBA8_LINEAR,0};
    arc_scene_metal_info source={7,2,192,96,11,12,13};uint64_t bytes=99;
    assert(arc_present_valid_scene_frame(&frame,&source,6,11,12,&bytes)&&bytes==192*96*4);
    bytes=99;assert(!arc_present_valid_scene_frame(&frame,&source,7,11,12,&bytes)&&bytes==99);
    assert(!arc_present_valid_scene_frame(&frame,&source,6,14,12,&bytes));
    assert(!arc_present_valid_scene_frame(&frame,&source,6,11,14,&bytes));
    source.frame_id=6;assert(!arc_present_valid_scene_frame(&frame,&source,5,11,12,&bytes));source.frame_id=7;
    source.width=64;assert(!arc_present_valid_scene_frame(&frame,&source,6,11,12,&bytes));source.width=192;
    source.generation=0;assert(!arc_present_valid_scene_frame(&frame,&source,6,11,12,&bytes));source.generation=2;
    frame.source_read_buffer=0x0405;assert(!arc_present_valid_scene_frame(&frame,&source,6,11,12,&bytes));frame.source_read_buffer=0;
    frame.color_encoding=ARC_PRESENT_RGBA8_SRGB;assert(!arc_present_valid_scene_frame(&frame,&source,6,11,12,&bytes));frame.color_encoding=ARC_PRESENT_RGBA8_LINEAR;
    frame.width=source.width=16384;frame.height=source.height=16384;assert(!arc_present_valid_scene_frame(&frame,&source,6,11,12,&bytes));
#if !defined(__APPLE__)
    assert(arc_present_scene_abi()==1 && arc_present_prepare_scene(NULL,NULL,&frame)==ARC_PRESENT_UNAVAILABLE);
#endif
    puts("Scene-to-present frame/provenance/queue/range admission and unchanged32B descriptor passed");return 0;
}
