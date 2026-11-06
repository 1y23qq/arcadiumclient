// SPDX-License-Identifier: GPL-3.0-only
#ifndef ARCADIUM_SCENE_PRESENTATION_H
#define ARCADIUM_SCENE_PRESENTATION_H
#include "arc_present.h"
#include "arc_scene_lease.h"
enum { ARC_PRESENT_SOURCE_IS_SCENE = 80, ARC_PRESENT_SOURCE_SCENE_FRAME = 81,
       ARC_PRESENT_SOURCE_SCENE_GENERATION = 82, ARC_PRESENT_SOURCE_CLAIM_PENDING = 83,
       ARC_PRESENT_SCENE_COMMAND_FAILED = 84 };
#ifdef __cplusplus
extern "C" {
#endif
ARC_API int arc_present_scene_abi(void);
// Render owner/current original CGL context. Claims the target only on success.
// Caller then releases its original lease handle; publish/hide owns GPU retirement.
ARC_API int arc_present_prepare_scene(void *bridge, void *target_lease, const arc_present_frame *frame);
int arc_present_valid_scene_frame(const arc_present_frame *frame, const arc_scene_metal_info *info,
    uint64_t previous_prepared, uint64_t expected_device, uint64_t expected_queue, uint64_t *bytes);
#ifdef __cplusplus
}
#endif
#endif
