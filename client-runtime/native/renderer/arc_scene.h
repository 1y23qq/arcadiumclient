// SPDX-License-Identifier: GPL-3.0-only
#ifndef ARCADIUM_RESIDENT_SCENE_H
#define ARCADIUM_RESIDENT_SCENE_H
#include <stdint.h>
#if defined(_WIN32)
#define ARC_API __declspec(dllexport)
#else
#define ARC_API __attribute__((visibility("default")))
#endif
#ifdef __cplusplus
extern "C" {
#endif

typedef enum { ARC_OK = 0, ARC_BUSY = 1, ARC_INVALID = -1, ARC_UNAVAILABLE = -2, ARC_FAILED = -3 } arc_scene_status;
typedef struct {
    uint32_t mesh_id, material_id, first_vertex, vertex_count, first_actor, actor_count;
} arc_scene_command;

ARC_API int arc_scene_abi(void);
ARC_API const char *arc_scene_error(void *context);
ARC_API void *arc_scene_create(int actor_capacity, int actor_stride, int vertex_stride, int uniform_bytes);
ARC_API void arc_scene_destroy(void *context);
ARC_API int arc_scene_pipeline(void *context, const char *source, const char *vertex, const char *fragment);
ARC_API int arc_scene_mesh(void *context, int id, const void *bytes, uint64_t length);
ARC_API int arc_scene_material(void *context, int id, int width, int height, const void *rgba, uint64_t length);
ARC_API int arc_scene_remove_mesh(void *context, int id);
ARC_API int arc_scene_remove_material(void *context, int id);
ARC_API int arc_scene_write(void *context, uint64_t offset, const void *bytes, uint64_t length);
ARC_API int arc_scene_submit(void *context, int width, int height, const void *uniforms, uint64_t uniform_length,
                             const arc_scene_command *commands, int command_count);
ARC_API int arc_scene_wait(void *context);
ARC_API int arc_scene_read_rgba(void *context, void *destination, uint64_t length);
ARC_API uint64_t arc_scene_query(void *context, int key);
ARC_API int arc_scene_reset(void *context);

int arc_scene_valid_range(uint64_t offset, uint64_t length, uint64_t capacity);
int arc_scene_valid_layout(int capacity, int actor_stride, int vertex_stride, int uniform_bytes);
int arc_scene_rgba_bytes(int width, int height, uint64_t *bytes);
#ifdef __cplusplus
}
#endif
#endif
