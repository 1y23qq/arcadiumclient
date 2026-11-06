// SPDX-License-Identifier: GPL-3.0-only
#ifndef ARCADIUM_SCENE_METAL_LEASE_H
#define ARCADIUM_SCENE_METAL_LEASE_H
#include "arc_scene.h"
enum { ARC_METAL_DEVICE_LEASE = 1, ARC_METAL_TARGET_LEASE = 2 };
typedef struct {
    uint64_t frame_id, generation;
    uint32_t width, height;
    uint64_t device, queue, texture;
} arc_scene_metal_info;
#ifdef __cplusplus
extern "C" {
#endif
ARC_API int arc_scene_metal_lease_abi(void);
// Native owner thread only. Object pointers remain borrowed from the returned lease.
ARC_API int arc_scene_metal_lease_acquire(void *context, int kind, uint64_t frame, void **out);
ARC_API int arc_scene_metal_lease_info(void *lease, arc_scene_metal_info *out);
// Close children first. Acceptance retires this handle behind the shared queue's GPU fence.
ARC_API int arc_scene_metal_lease_release(void *lease);
#ifdef __cplusplus
}
#endif
#endif
