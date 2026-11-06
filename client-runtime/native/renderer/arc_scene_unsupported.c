// SPDX-License-Identifier: GPL-3.0-only
#include "arc_scene.h"
#include "arc_scene_passes.h"

static const char message[1024] = "Metal is unavailable on this operating system; keep the OpenGL backend";
int arc_scene_abi(void) { return 1; }
const char *arc_scene_error(void *context) { (void)context; return message; }
void *arc_scene_create(int capacity, int actor_stride, int vertex_stride, int uniform_bytes) {
    (void)capacity; (void)actor_stride; (void)vertex_stride; (void)uniform_bytes; return 0;
}
void arc_scene_destroy(void *context) { (void)context; }
int arc_scene_pipeline(void *c, const char *s, const char *v, const char *f) { (void)c; (void)s; (void)v; (void)f; return ARC_UNAVAILABLE; }
int arc_scene_mesh(void *c, int id, const void *b, uint64_t n) { (void)c; (void)id; (void)b; (void)n; return ARC_UNAVAILABLE; }
int arc_scene_material(void *c, int id, int w, int h, const void *b, uint64_t n) { (void)c; (void)id; (void)w; (void)h; (void)b; (void)n; return ARC_UNAVAILABLE; }
int arc_scene_remove_mesh(void *c, int id) { (void)c; (void)id; return ARC_UNAVAILABLE; }
int arc_scene_remove_material(void *c, int id) { (void)c; (void)id; return ARC_UNAVAILABLE; }
int arc_scene_write(void *c, uint64_t o, const void *b, uint64_t n) { (void)c; (void)o; (void)b; (void)n; return ARC_UNAVAILABLE; }
int arc_scene_submit(void *c, int w, int h, const void *u, uint64_t n, const arc_scene_command *d, int count) { (void)c; (void)w; (void)h; (void)u; (void)n; (void)d; (void)count; return ARC_UNAVAILABLE; }
int arc_scene_wait(void *c) { (void)c; return ARC_UNAVAILABLE; }
int arc_scene_read_rgba(void *c, void *d, uint64_t n) { (void)c; (void)d; (void)n; return ARC_UNAVAILABLE; }
uint64_t arc_scene_query(void *c, int key) { (void)c; (void)key; return 0; }
int arc_scene_reset(void *c) { (void)c; return ARC_UNAVAILABLE; }
int arc_scene_pass_abi(void) { return 1; }
int arc_scene_pipeline_state_put(void *c, int id, const char *s, const char *v, const char *f, const arc_scene_pipeline_state *p) {
    (void)c; (void)id; (void)s; (void)v; (void)f; (void)p; return ARC_UNAVAILABLE;
}
int arc_scene_pipeline_state_remove(void *c, int id) { (void)c; (void)id; return ARC_UNAVAILABLE; }
int arc_scene_submit_passes(void *c, int w, int h, const void *u, uint64_t n, const arc_scene_pass *p, int pc,
    const arc_scene_command *d, int dc) {
    (void)c; (void)w; (void)h; (void)u; (void)n; (void)p; (void)pc; (void)d; (void)dc; return ARC_UNAVAILABLE;
}
int arc_scene_read_depth(void *c, void *d, uint64_t n) { (void)c; (void)d; (void)n; return ARC_UNAVAILABLE; }
