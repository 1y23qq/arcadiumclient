// SPDX-License-Identifier: GPL-3.0-only
#ifndef ARCADIUM_METAL_OBJECTS_H
#define ARCADIUM_METAL_OBJECTS_H
#import <Metal/Metal.h>
int arc_scene_metal_objects(void *scene, id<MTLDevice> __strong *device, id<MTLCommandQueue> __strong *queue);
#endif
