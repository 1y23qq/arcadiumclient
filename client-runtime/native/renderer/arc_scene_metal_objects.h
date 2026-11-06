// SPDX-License-Identifier: GPL-3.0-only
#ifndef ARCADIUM_METAL_OBJECTS_H
#define ARCADIUM_METAL_OBJECTS_H
#import <Metal/Metal.h>
#include "arc_scene_lease.h"
int arc_scene_metal_objects(void *scene, id<MTLDevice> __strong *device, id<MTLCommandQueue> __strong *queue);
int arc_scene_metal_target_snapshot(void *lease, void *scene, arc_scene_metal_info *info, id<MTLTexture> __strong *texture);
int arc_scene_metal_target_claim(void *lease, void *scene, uint64_t frame, uint64_t generation, id __strong *claim);
int arc_scene_metal_target_retire(id claim);
int arc_scene_metal_target_failed(id claim);
#endif
