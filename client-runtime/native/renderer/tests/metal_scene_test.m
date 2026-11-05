// SPDX-License-Identifier: GPL-3.0-only
#include "../arc_scene.h"
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CHECK(expression) do { if (!(expression)) { fprintf(stderr, "Metal scene check failed at line %d: %s (%s)\n", __LINE__, #expression, arc_scene_error(context)); arc_scene_destroy(context); return 1; } } while (0)

static int pixel_matches(const uint8_t *bytes, int width, int height, int red, int green, int blue) {
    const uint8_t *pixel = bytes + ((height / 2) * width + width / 2) * 4;
    return abs((int) pixel[0] - red) <= 1 && abs((int) pixel[1] - green) <= 1
        && abs((int) pixel[2] - blue) <= 1 && pixel[3] == 255;
}

int main(int argc, const char **argv) {
    @autoreleasepool {
        if (argc != 2) { fputs("Pass the reference Metal shader path\n", stderr); return 1; }
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (!device) { puts("SKIP: This machine exposes no Metal device"); return 77; }
        NSError *failure = nil;
        NSString *source = [NSString stringWithContentsOfFile:[NSString stringWithUTF8String:argv[1]] encoding:NSUTF8StringEncoding error:&failure];
        if (!source) { fprintf(stderr, "Shader read failed: %s\n", failure.localizedDescription.UTF8String); return 1; }
        void *context = arc_scene_create(2, 80, 20, 64);
        CHECK(context != NULL);
        CHECK(arc_scene_pipeline(context, source.UTF8String, "vertexMain", "fragmentMain") == ARC_OK);
        const float vertices[] = {-1, -1, 0, .5f, .5f, 1, -1, 0, .5f, .5f, 0, 1, 0, .5f, .5f};
        const uint8_t material[] = {51, 102, 204, 255};
        float actor[20] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1};
        const float camera[] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        arc_scene_command draw = {1, 2, 0, 3, 1, 1};
        CHECK(arc_scene_mesh(context, 1, vertices, sizeof(vertices)) == ARC_OK);
        CHECK(arc_scene_material(context, 2, 1, 1, material, sizeof(material)) == ARC_OK);
        CHECK(arc_scene_write(context, 80, actor, sizeof(actor)) == ARC_OK);
        CHECK(arc_scene_submit(context, 64, 64, camera, sizeof(camera), &draw, 1) == ARC_OK);
        uint8_t pixels[96 * 64 * 4];
        CHECK(arc_scene_read_rgba(context, pixels, 64 * 64 * 4) == ARC_OK);
        CHECK(pixel_matches(pixels, 64, 64, 51, 102, 204));
        float tint[] = {1, .5f, 1, 1};
        CHECK(arc_scene_write(context, 80 + 64, tint, sizeof(tint)) == ARC_OK);
        CHECK(arc_scene_submit(context, 96, 48, camera, sizeof(camera), &draw, 1) == ARC_OK);
        CHECK(arc_scene_read_rgba(context, pixels, 96 * 48 * 4) == ARC_OK);
        CHECK(pixel_matches(pixels, 96, 48, 51, 51, 204));
        CHECK(arc_scene_query(context, 4) == 96);
        CHECK(arc_scene_query(context, 0) == 2 && arc_scene_query(context, 1) == 2);
        arc_scene_command invalid = draw;
        invalid.first_actor = 2;
        CHECK(arc_scene_submit(context, 96, 48, camera, sizeof(camera), &invalid, 1) == ARC_INVALID);
        invalid = draw; invalid.vertex_count = 4;
        CHECK(arc_scene_submit(context, 96, 48, camera, sizeof(camera), &invalid, 1) == ARC_INVALID);
        CHECK(arc_scene_reset(context) == ARC_OK);
        CHECK(arc_scene_submit(context, 96, 48, camera, sizeof(camera), &draw, 1) == ARC_INVALID);
        CHECK(arc_scene_mesh(context, 1, vertices, sizeof(vertices)) == ARC_OK);
        CHECK(arc_scene_material(context, 2, 1, 1, material, sizeof(material)) == ARC_OK);
        CHECK(arc_scene_write(context, 80, actor, sizeof(actor)) == ARC_OK);
        CHECK(arc_scene_submit(context, 64, 64, camera, sizeof(camera), &draw, 1) == ARC_OK);
        CHECK(arc_scene_read_rgba(context, pixels, 64 * 64 * 4) == ARC_OK);
        CHECK(pixel_matches(pixels, 64, 64, 51, 102, 204));
        uint64_t uploaded = arc_scene_query(context, 4);
        CHECK(arc_scene_submit(context, 64, 64, camera, sizeof(camera), &draw, 1) == ARC_OK);
        arc_scene_destroy(context);
        printf("Metal device: %s\n", device.name.UTF8String);
        printf("Metal scene passed: resident mesh/material, base instance, dirty update, readback, resize, invalid commands, reset, close after submission; actor upload bytes=%llu\n", (unsigned long long) uploaded);
        return 0;
    }
}
