// SPDX-License-Identifier: GPL-3.0-only
#include "arc_scene.h"
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <dispatch/dispatch.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

static _Thread_local char arc_global_error[1024];
static const uint64_t arc_resident_limit = UINT64_C(512) * 1024 * 1024;

@interface ArcResidentScene : NSObject {
@public char error[1024];
}
@property(nonatomic, strong) id<MTLDevice> device;
@property(nonatomic, strong) id<MTLCommandQueue> queue;
@property(nonatomic, strong) id<MTLBuffer> actors;
@property(nonatomic, strong) id<MTLBuffer> uniforms;
@property(nonatomic, strong) id<MTLRenderPipelineState> pipeline;
@property(nonatomic, strong) id<MTLDepthStencilState> depthState;
@property(nonatomic, strong) id<MTLTexture> color;
@property(nonatomic, strong) id<MTLTexture> depth;
@property(nonatomic, strong) id<MTLTexture> white;
@property(nonatomic, strong) NSMutableDictionary<NSNumber *, id<MTLBuffer>> *meshes;
@property(nonatomic, strong) NSMutableDictionary<NSNumber *, id<MTLTexture>> *materials;
@property(nonatomic, strong) dispatch_semaphore_t permit;
@property(nonatomic) NSUInteger actorCapacity;
@property(nonatomic) NSUInteger vertexStride;
@property(nonatomic) uint64_t meshBytes;
@property(nonatomic) uint64_t materialBytes;
@property(nonatomic) uint64_t submitted;
@property(atomic) uint64_t completed;
@property(nonatomic) uint64_t busy;
@property(atomic) uint64_t drawCalls;
@property(nonatomic) uint64_t uploaded;
@property(atomic) uint64_t gpuNanoseconds;
@property(atomic) BOOL commandFailed;
@end
@implementation ArcResidentScene
@end

static ArcResidentScene *arc_context(void *pointer) { return (__bridge ArcResidentScene *) pointer; }
static int arc_fail(ArcResidentScene *scene, NSString *message, int status) {
    snprintf(scene ? scene->error : arc_global_error, 1024, "%s", message.UTF8String ?: "Unknown Metal error");
    return status;
}
static BOOL arc_acquire(ArcResidentScene *scene) {
    return scene && dispatch_semaphore_wait(scene.permit, DISPATCH_TIME_NOW) == 0;
}
static void arc_release(ArcResidentScene *scene) { dispatch_semaphore_signal(scene.permit); }
static BOOL arc_id(int identifier) { return identifier >= 1 && identifier <= 4095; }
static uint64_t arc_resident_bytes(ArcResidentScene *scene) {
    return scene.actors.length + scene.uniforms.length + scene.meshBytes + scene.materialBytes + 4
        + (uint64_t) scene.color.width * scene.color.height * 8;
}

int arc_scene_abi(void) { return 1; }
const char *arc_scene_error(void *pointer) {
    ArcResidentScene *scene = arc_context(pointer);
    return scene ? scene->error : arc_global_error;
}

void *arc_scene_create(int capacity, int actor_stride, int vertex_stride, int uniform_bytes) {
    @autoreleasepool {
        if (!arc_scene_valid_layout(capacity, actor_stride, vertex_stride, uniform_bytes)) {
            arc_fail(nil, @"Invalid resident scene layout", ARC_INVALID); return NULL;
        }
        ArcResidentScene *scene = [ArcResidentScene new];
        scene.device = MTLCreateSystemDefaultDevice();
        if (!scene.device) { arc_fail(nil, @"No Metal device is available", ARC_UNAVAILABLE); return NULL; }
        scene.queue = [scene.device newCommandQueue];
        scene.actors = [scene.device newBufferWithLength:(NSUInteger) capacity * actor_stride options:MTLResourceStorageModeShared];
        scene.uniforms = [scene.device newBufferWithLength:(NSUInteger) uniform_bytes options:MTLResourceStorageModeShared];
        if (!scene.queue || !scene.actors || !scene.uniforms) {
            arc_fail(nil, @"Metal could not allocate the resident scene", ARC_FAILED); return NULL;
        }
        scene.actorCapacity = (NSUInteger) capacity;
        scene.vertexStride = (NSUInteger) vertex_stride;
        scene.meshes = [NSMutableDictionary new];
        scene.materials = [NSMutableDictionary new];
        scene.permit = dispatch_semaphore_create(1);
        memset(scene.actors.contents, 0, scene.actors.length);
        MTLTextureDescriptor *white = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm width:1 height:1 mipmapped:NO];
        white.storageMode = scene.device.hasUnifiedMemory ? MTLStorageModeShared : MTLStorageModeManaged;
        white.usage = MTLTextureUsageShaderRead;
        scene.white = [scene.device newTextureWithDescriptor:white];
        if (!scene.white) { arc_fail(nil, @"Metal could not allocate the default material", ARC_FAILED); return NULL; }
        uint8_t pixel[4] = {255, 255, 255, 255};
        [scene.white replaceRegion:MTLRegionMake2D(0, 0, 1, 1) mipmapLevel:0 withBytes:pixel bytesPerRow:4];
        return (__bridge_retained void *) scene;
    }
}

void arc_scene_destroy(void *pointer) {
    @autoreleasepool {
        if (!pointer) return;
        ArcResidentScene *scene = (__bridge_transfer ArcResidentScene *) pointer;
        if (dispatch_semaphore_wait(scene.permit, dispatch_time(DISPATCH_TIME_NOW, INT64_C(5000000000))) == 0)
            dispatch_semaphore_signal(scene.permit);
    }
}

int arc_scene_pipeline(void *pointer, const char *source, const char *vertex_name, const char *fragment_name) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene || !source || !vertex_name || !fragment_name) return ARC_INVALID;
        if (!arc_acquire(scene)) return ARC_BUSY;
        NSError *failure = nil;
        MTLCompileOptions *options = [MTLCompileOptions new];
        options.languageVersion = MTLLanguageVersion2_3;
        id<MTLLibrary> library = [scene.device newLibraryWithSource:[NSString stringWithUTF8String:source] options:options error:&failure];
        id<MTLFunction> vertex = [library newFunctionWithName:[NSString stringWithUTF8String:vertex_name]];
        id<MTLFunction> fragment = [library newFunctionWithName:[NSString stringWithUTF8String:fragment_name]];
        if (!library || !vertex || !fragment) {
            int result = arc_fail(scene, failure.localizedDescription ?: @"Metal shader entry point is missing", ARC_FAILED);
            arc_release(scene); return result;
        }
        MTLRenderPipelineDescriptor *descriptor = [MTLRenderPipelineDescriptor new];
        descriptor.vertexFunction = vertex;
        descriptor.fragmentFunction = fragment;
        descriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
        descriptor.colorAttachments[0].blendingEnabled = YES;
        descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorSourceAlpha;
        descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
        descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
        descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
        descriptor.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
        id<MTLRenderPipelineState> pipeline = [scene.device newRenderPipelineStateWithDescriptor:descriptor error:&failure];
        MTLDepthStencilDescriptor *depth = [MTLDepthStencilDescriptor new];
        depth.depthCompareFunction = MTLCompareFunctionLessEqual;
        depth.depthWriteEnabled = YES;
        id<MTLDepthStencilState> depth_state = [scene.device newDepthStencilStateWithDescriptor:depth];
        int result = ARC_OK;
        if (!pipeline || !depth_state) result = arc_fail(scene, failure.localizedDescription ?: @"Metal pipeline creation failed", ARC_FAILED);
        else { scene.pipeline = pipeline; scene.depthState = depth_state; }
        arc_release(scene); return result;
    }
}

int arc_scene_mesh(void *pointer, int identifier, const void *bytes, uint64_t length) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene || !arc_id(identifier) || !bytes || !length || length > UINT64_C(32) * 1024 * 1024 || length % scene.vertexStride) return ARC_INVALID;
        if (!arc_acquire(scene)) return ARC_BUSY;
        id<MTLBuffer> old = scene.meshes[@(identifier)];
        uint64_t resident = arc_resident_bytes(scene) - old.length + length;
        if (resident > arc_resident_limit) { arc_release(scene); return arc_fail(scene, @"Resident scene memory budget exceeded", ARC_INVALID); }
        id<MTLBuffer> replacement = [scene.device newBufferWithBytes:bytes length:(NSUInteger) length options:MTLResourceStorageModeShared];
        if (!replacement) { arc_release(scene); return arc_fail(scene, @"Metal mesh allocation failed", ARC_FAILED); }
        scene.meshes[@(identifier)] = replacement;
        scene.meshBytes = scene.meshBytes - old.length + length;
        arc_release(scene); return ARC_OK;
    }
}

int arc_scene_material(void *pointer, int identifier, int width, int height, const void *rgba, uint64_t length) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        uint64_t expected = 0;
        if (!scene || !arc_id(identifier) || !rgba || !arc_scene_rgba_bytes(width, height, &expected) || length != expected) return ARC_INVALID;
        if (!arc_acquire(scene)) return ARC_BUSY;
        id<MTLTexture> old = scene.materials[@(identifier)];
        uint64_t old_bytes = (uint64_t) old.width * old.height * 4;
        if (arc_resident_bytes(scene) - old_bytes + length > arc_resident_limit) { arc_release(scene); return arc_fail(scene, @"Resident scene memory budget exceeded", ARC_INVALID); }
        MTLTextureDescriptor *descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm width:(NSUInteger) width height:(NSUInteger) height mipmapped:NO];
        descriptor.usage = MTLTextureUsageShaderRead;
        descriptor.storageMode = scene.device.hasUnifiedMemory ? MTLStorageModeShared : MTLStorageModeManaged;
        id<MTLTexture> replacement = [scene.device newTextureWithDescriptor:descriptor];
        if (!replacement) { arc_release(scene); return arc_fail(scene, @"Metal material allocation failed", ARC_FAILED); }
        [replacement replaceRegion:MTLRegionMake2D(0, 0, (NSUInteger) width, (NSUInteger) height) mipmapLevel:0 withBytes:rgba bytesPerRow:(NSUInteger) width * 4];
        scene.materials[@(identifier)] = replacement;
        scene.materialBytes = scene.materialBytes - old_bytes + length;
        arc_release(scene); return ARC_OK;
    }
}

int arc_scene_remove_mesh(void *pointer, int identifier) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene || !arc_id(identifier)) return ARC_INVALID;
        if (!arc_acquire(scene)) return ARC_BUSY;
        scene.meshBytes -= scene.meshes[@(identifier)].length;
        [scene.meshes removeObjectForKey:@(identifier)];
        arc_release(scene); return ARC_OK;
    }
}

int arc_scene_remove_material(void *pointer, int identifier) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene || !arc_id(identifier)) return ARC_INVALID;
        if (!arc_acquire(scene)) return ARC_BUSY;
        id<MTLTexture> texture = scene.materials[@(identifier)];
        scene.materialBytes -= (uint64_t) texture.width * texture.height * 4;
        [scene.materials removeObjectForKey:@(identifier)];
        arc_release(scene); return ARC_OK;
    }
}

int arc_scene_write(void *pointer, uint64_t offset, const void *bytes, uint64_t length) {
    ArcResidentScene *scene = arc_context(pointer);
    if (!scene || (length && !bytes) || !arc_scene_valid_range(offset, length, scene.actors.length)) return ARC_INVALID;
    if (!arc_acquire(scene)) return ARC_BUSY;
    if (length) memcpy((uint8_t *) scene.actors.contents + offset, bytes, (size_t) length);
    scene.uploaded += length;
    arc_release(scene); return ARC_OK;
}

static int arc_targets(ArcResidentScene *scene, int width, int height) {
    if (scene.color.width == (NSUInteger) width && scene.color.height == (NSUInteger) height) return ARC_OK;
    uint64_t size = 0;
    if (!arc_scene_rgba_bytes(width, height, &size)) return ARC_INVALID;
    uint64_t old = (uint64_t) scene.color.width * scene.color.height * 8;
    if (arc_resident_bytes(scene) - old + size * 2 > arc_resident_limit) return arc_fail(scene, @"Resident scene memory budget exceeded", ARC_INVALID);
    MTLTextureDescriptor *color_descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm width:(NSUInteger) width height:(NSUInteger) height mipmapped:NO];
    color_descriptor.storageMode = scene.device.hasUnifiedMemory ? MTLStorageModeShared : MTLStorageModeManaged;
    color_descriptor.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;
    MTLTextureDescriptor *depth_descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatDepth32Float width:(NSUInteger) width height:(NSUInteger) height mipmapped:NO];
    depth_descriptor.storageMode = MTLStorageModePrivate;
    depth_descriptor.usage = MTLTextureUsageRenderTarget;
    id<MTLTexture> color = [scene.device newTextureWithDescriptor:color_descriptor];
    id<MTLTexture> depth = [scene.device newTextureWithDescriptor:depth_descriptor];
    if (!color || !depth) return arc_fail(scene, @"Metal target allocation failed", ARC_FAILED);
    scene.color = color; scene.depth = depth;
    return ARC_OK;
}

int arc_scene_submit(void *pointer, int width, int height, const void *uniforms, uint64_t uniform_length,
                     const arc_scene_command *commands, int command_count) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene || !scene.pipeline || !uniforms || uniform_length != scene.uniforms.length || command_count < 0
            || command_count > 65536 || (command_count && !commands)) return ARC_INVALID;
        if (!arc_acquire(scene)) { scene.busy++; return ARC_BUSY; }
        if (scene.commandFailed) { arc_release(scene); return ARC_FAILED; }
        for (int i = 0; i < command_count; i++) {
            arc_scene_command draw = commands[i];
            id<MTLBuffer> vertices = scene.meshes[@(draw.mesh_id)];
            if (!arc_id((int) draw.mesh_id) || !vertices || (draw.material_id && !scene.materials[@(draw.material_id)])
                || !arc_scene_valid_range(draw.first_vertex, draw.vertex_count, vertices.length / scene.vertexStride)
                || !arc_scene_valid_range(draw.first_actor, draw.actor_count, scene.actorCapacity)) {
                arc_release(scene); return arc_fail(scene, @"Draw command references missing resources or an invalid range", ARC_INVALID);
            }
        }
        int result = arc_targets(scene, width, height);
        if (result != ARC_OK) { arc_release(scene); return result; }
        memcpy(scene.uniforms.contents, uniforms, (size_t) uniform_length);
        id<MTLCommandBuffer> command = [scene.queue commandBuffer];
        MTLRenderPassDescriptor *pass = [MTLRenderPassDescriptor new];
        pass.colorAttachments[0].texture = scene.color;
        pass.colorAttachments[0].loadAction = MTLLoadActionClear;
        pass.colorAttachments[0].storeAction = MTLStoreActionStore;
        pass.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0);
        pass.depthAttachment.texture = scene.depth;
        pass.depthAttachment.loadAction = MTLLoadActionClear;
        pass.depthAttachment.storeAction = MTLStoreActionDontCare;
        pass.depthAttachment.clearDepth = 1;
        id<MTLRenderCommandEncoder> encoder = [command renderCommandEncoderWithDescriptor:pass];
        if (!command || !encoder) { arc_release(scene); return arc_fail(scene, @"Metal command encoding failed", ARC_FAILED); }
        [encoder setRenderPipelineState:scene.pipeline];
        [encoder setDepthStencilState:scene.depthState];
        [encoder setCullMode:MTLCullModeNone];
        [encoder setVertexBuffer:scene.actors offset:0 atIndex:1];
        [encoder setVertexBuffer:scene.uniforms offset:0 atIndex:2];
        uint64_t draws = 0;
        for (int i = 0; i < command_count; i++) {
            arc_scene_command draw = commands[i];
            if (!draw.vertex_count || !draw.actor_count) continue;
            [encoder setVertexBuffer:scene.meshes[@(draw.mesh_id)] offset:0 atIndex:0];
            [encoder setFragmentTexture:draw.material_id ? scene.materials[@(draw.material_id)] : scene.white atIndex:0];
            [encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:draw.first_vertex vertexCount:draw.vertex_count instanceCount:draw.actor_count baseInstance:draw.first_actor];
            draws++;
        }
        [encoder endEncoding];
        scene.submitted++;
        scene.commandFailed = NO;
        __weak ArcResidentScene *weak_scene = scene;
        [command addCompletedHandler:^(id<MTLCommandBuffer> completed) {
            ArcResidentScene *retained = weak_scene;
            if (!retained) return;
            retained.completed++;
            retained.drawCalls += draws;
            double elapsed = completed.GPUEndTime - completed.GPUStartTime;
            retained.gpuNanoseconds = isfinite(elapsed) && elapsed > 0 ? (uint64_t) (elapsed * 1e9) : 0;
            if (completed.status == MTLCommandBufferStatusError) {
                retained.commandFailed = YES;
                arc_fail(retained, completed.error.localizedDescription ?: @"Metal command execution failed", ARC_FAILED);
            }
            arc_release(retained);
        }];
        [command commit];
        return ARC_OK;
    }
}

int arc_scene_wait(void *pointer) {
    ArcResidentScene *scene = arc_context(pointer);
    if (!scene) return ARC_INVALID;
    if (dispatch_semaphore_wait(scene.permit, dispatch_time(DISPATCH_TIME_NOW, INT64_C(5000000000))) != 0) {
        return arc_fail(scene, @"Metal frame did not complete within five seconds", ARC_FAILED);
    }
    BOOL failed = scene.commandFailed;
    arc_release(scene); return failed ? ARC_FAILED : ARC_OK;
}

int arc_scene_read_rgba(void *pointer, void *destination, uint64_t length) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene || !destination || !scene.color || length != (uint64_t) scene.color.width * scene.color.height * 4) return ARC_INVALID;
        int result = arc_scene_wait(pointer);
        if (result != ARC_OK) return result;
        if (!arc_acquire(scene)) return ARC_BUSY;
        if (scene.color.storageMode == MTLStorageModeManaged) {
            id<MTLCommandBuffer> command = [scene.queue commandBuffer];
            id<MTLBlitCommandEncoder> blit = [command blitCommandEncoder];
            if (!command || !blit) { arc_release(scene); return arc_fail(scene, @"Metal readback synchronization failed", ARC_FAILED); }
            [blit synchronizeResource:scene.color];
            [blit endEncoding];
            dispatch_semaphore_t completed = dispatch_semaphore_create(0);
            __weak ArcResidentScene *weak_scene = scene;
            [command addCompletedHandler:^(id<MTLCommandBuffer> finished) {
                ArcResidentScene *retained = weak_scene;
                if (retained) {
                    if (finished.status == MTLCommandBufferStatusError) {
                        retained.commandFailed = YES;
                        arc_fail(retained, finished.error.localizedDescription ?: @"Metal readback failed", ARC_FAILED);
                    }
                    arc_release(retained);
                }
                dispatch_semaphore_signal(completed);
            }];
            [command commit];
            if (dispatch_semaphore_wait(completed, dispatch_time(DISPATCH_TIME_NOW, INT64_C(5000000000))) != 0) {
                scene.commandFailed = YES;
                return arc_fail(scene, @"Metal readback did not complete within five seconds", ARC_FAILED);
            }
            if (!arc_acquire(scene)) return ARC_BUSY;
            if (command.status == MTLCommandBufferStatusError) {
                arc_release(scene); return arc_fail(scene, command.error.localizedDescription ?: @"Metal readback failed", ARC_FAILED);
            }
        }
        [scene.color getBytes:destination bytesPerRow:scene.color.width * 4 fromRegion:MTLRegionMake2D(0, 0, scene.color.width, scene.color.height) mipmapLevel:0];
        uint8_t *bytes = destination;
        for (uint64_t i = 0; i < length; i += 4) { uint8_t red = bytes[i + 2]; bytes[i + 2] = bytes[i]; bytes[i] = red; }
        arc_release(scene); return ARC_OK;
    }
}

uint64_t arc_scene_query(void *pointer, int key) {
    ArcResidentScene *scene = arc_context(pointer);
    if (!scene) return 0;
    switch (key) {
        case 0: return scene.submitted;
        case 1: return scene.completed;
        case 2: return scene.busy;
        case 3: return scene.drawCalls;
        case 4: return scene.uploaded;
        case 5: return arc_resident_bytes(scene);
        case 6: return scene.gpuNanoseconds;
        default: return 0;
    }
}

int arc_scene_reset(void *pointer) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene) return ARC_INVALID;
        int result = arc_scene_wait(pointer);
        if (result != ARC_OK) return result;
        if (!arc_acquire(scene)) return ARC_BUSY;
        [scene.meshes removeAllObjects];
        [scene.materials removeAllObjects];
        scene.meshBytes = scene.materialBytes = 0;
        scene.color = nil; scene.depth = nil;
        memset(scene.actors.contents, 0, scene.actors.length);
        memset(scene.uniforms.contents, 0, scene.uniforms.length);
        arc_release(scene); return ARC_OK;
    }
}
