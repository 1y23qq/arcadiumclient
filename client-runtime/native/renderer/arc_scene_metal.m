// SPDX-License-Identifier: GPL-3.0-only
#include "arc_scene.h"
#include "arc_scene_passes.h"
#include "arc_scene_lease.h"
#include "arc_scene_metal_objects.h"
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <dispatch/dispatch.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>

static _Thread_local char arc_global_error[1024];
static const uint64_t arc_resident_limit = UINT64_C(512) * 1024 * 1024;

@interface ArcResidentPipeline : NSObject {
@public arc_scene_pipeline_state state;
}
@property(nonatomic, strong) id<MTLRenderPipelineState> pipeline;
@property(nonatomic, strong) id<MTLDepthStencilState> depth;
@end
@implementation ArcResidentPipeline
@end

@interface ArcResidentScene : NSObject {
@public char error[1024];
@public atomic_int deviceLease, targetLease;
@public atomic_bool frameInFlight;
@public pthread_t ownerThread;
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
@property(nonatomic, strong) NSMutableDictionary<NSNumber *, ArcResidentPipeline *> *passPipelines;
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
@property(nonatomic) uint32_t storedAttachments;
@property(atomic) uint64_t completedPasses;
@property(nonatomic, strong) dispatch_group_t externalWork;
@property(atomic) BOOL externallyClosed;
@property(nonatomic) uint64_t targetGeneration;
@end
@implementation ArcResidentScene
@end

static ArcResidentScene *arc_context(void *pointer) { return (__bridge ArcResidentScene *) pointer; }
int arc_scene_metal_objects(void *pointer, id<MTLDevice> __strong *device, id<MTLCommandQueue> __strong *queue) {
    if (!pointer || !device || !queue) return ARC_INVALID;
    ArcResidentScene *scene = arc_context(pointer);
    if (!scene.device || !scene.queue) return ARC_UNAVAILABLE;
    *device = scene.device; *queue = scene.queue;
    return ARC_OK;
}
static int arc_fail(ArcResidentScene *scene, NSString *message, int status) {
    snprintf(scene ? scene->error : arc_global_error, 1024, "%s", message.UTF8String ?: "Unknown Metal error");
    return status;
}
static BOOL arc_acquire(ArcResidentScene *scene) {
    if (!scene || scene.externallyClosed || atomic_load(&scene->targetLease) != 0
        || dispatch_semaphore_wait(scene.permit, DISPATCH_TIME_NOW) != 0) return NO;
    if (scene.externallyClosed || atomic_load(&scene->targetLease) != 0) {
        dispatch_semaphore_signal(scene.permit); return NO;
    }
    return YES;
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
        scene->ownerThread = pthread_self(); atomic_init(&scene->deviceLease, 0); atomic_init(&scene->targetLease, 0);
        atomic_init(&scene->frameInFlight, false); scene.externalWork = dispatch_group_create();
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
        scene.passPipelines = [NSMutableDictionary new];
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
        scene.externallyClosed = YES;
        dispatch_time_t deadline = dispatch_time(DISPATCH_TIME_NOW, INT64_C(5000000000));
        if (atomic_load(&scene->targetLease) != 1) (void)dispatch_group_wait(scene.externalWork, deadline);
        if (dispatch_semaphore_wait(scene.permit, deadline) == 0)
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
    if (scene.targetGeneration == UINT64_MAX) return ARC_INVALID;
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
    scene.color = color; scene.depth = depth; scene.storedAttachments = 0; scene.targetGeneration++;
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
        scene.storedAttachments = ARC_ATTACH_COLOR;
        scene.submitted++;
        scene.commandFailed = NO;
        atomic_store(&scene->frameInFlight, true);
        [command addCompletedHandler:^(id<MTLCommandBuffer> completed) {
            ArcResidentScene *retained = scene;
            retained.completed++;
            retained.drawCalls += draws;
            double elapsed = completed.GPUEndTime - completed.GPUStartTime;
            retained.gpuNanoseconds = isfinite(elapsed) && elapsed > 0 ? (uint64_t) (elapsed * 1e9) : 0;
            if (completed.status == MTLCommandBufferStatusError) {
                retained.commandFailed = YES;
                arc_fail(retained, completed.error.localizedDescription ?: @"Metal command execution failed", ARC_FAILED);
            }
            atomic_store(&retained->frameInFlight, false); arc_release(retained);
        }];
        [command commit];
        return ARC_OK;
    }
}

int arc_scene_wait(void *pointer) {
    ArcResidentScene *scene = arc_context(pointer);
    if (!scene || scene.externallyClosed) return ARC_INVALID;
    if (atomic_load(&scene->targetLease) == 1) return ARC_BUSY;
    dispatch_time_t deadline = dispatch_time(DISPATCH_TIME_NOW, INT64_C(5000000000));
    if (dispatch_group_wait(scene.externalWork, deadline) != 0 || dispatch_semaphore_wait(scene.permit, deadline) != 0) {
        return arc_fail(scene, @"Metal frame did not complete within five seconds", ARC_FAILED);
    }
    BOOL failed = scene.commandFailed;
    arc_release(scene); return failed ? ARC_FAILED : ARC_OK;
}

int arc_scene_pass_abi(void) { return 1; }

int arc_scene_pipeline_state_put(void *pointer, int identifier, const char *source,
    const char *vertex_name, const char *fragment_name, const arc_scene_pipeline_state *state) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene || identifier < 1 || identifier > ARC_PIPELINE_LIMIT || !source || !vertex_name || !fragment_name
            || !arc_scene_valid_pipeline_state(state) || strnlen(source, 131073) > 131072
            || strnlen(vertex_name, 257) > 256 || strnlen(fragment_name, 257) > 256) return ARC_INVALID;
        if (!arc_acquire(scene)) return ARC_BUSY;
        NSError *error = nil;
        MTLCompileOptions *options = [MTLCompileOptions new]; options.languageVersion = MTLLanguageVersion2_3;
        id<MTLLibrary> library = [scene.device newLibraryWithSource:[NSString stringWithUTF8String:source] options:options error:&error];
        id<MTLFunction> vertex = [library newFunctionWithName:[NSString stringWithUTF8String:vertex_name]];
        id<MTLFunction> fragment = [library newFunctionWithName:[NSString stringWithUTF8String:fragment_name]];
        if (!library || !vertex || !fragment) {
            arc_release(scene); return arc_fail(scene, error.localizedDescription ?: @"Pass shader entry point is missing", ARC_FAILED);
        }
        const MTLBlendFactor factors[] = {MTLBlendFactorZero, MTLBlendFactorOne, MTLBlendFactorSourceColor,
            MTLBlendFactorOneMinusSourceColor, MTLBlendFactorDestinationColor, MTLBlendFactorOneMinusDestinationColor,
            MTLBlendFactorSourceAlpha, MTLBlendFactorOneMinusSourceAlpha, MTLBlendFactorDestinationAlpha, MTLBlendFactorOneMinusDestinationAlpha};
        const MTLBlendOperation operations[] = {MTLBlendOperationAdd, MTLBlendOperationSubtract,
            MTLBlendOperationReverseSubtract, MTLBlendOperationMin, MTLBlendOperationMax};
        const MTLCompareFunction comparisons[] = {MTLCompareFunctionNever, MTLCompareFunctionLess,
            MTLCompareFunctionEqual, MTLCompareFunctionLessEqual, MTLCompareFunctionGreater,
            MTLCompareFunctionNotEqual, MTLCompareFunctionGreaterEqual, MTLCompareFunctionAlways};
        MTLRenderPipelineDescriptor *descriptor = [MTLRenderPipelineDescriptor new];
        descriptor.vertexFunction = vertex; descriptor.fragmentFunction = fragment;
        descriptor.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
        MTLRenderPipelineColorAttachmentDescriptor *color = descriptor.colorAttachments[0];
        color.pixelFormat = MTLPixelFormatBGRA8Unorm;
        color.blendingEnabled = (state->flags & ARC_PIPE_BLEND) != 0;
        color.sourceRGBBlendFactor = factors[state->src_rgb]; color.destinationRGBBlendFactor = factors[state->dst_rgb];
        color.sourceAlphaBlendFactor = factors[state->src_alpha]; color.destinationAlphaBlendFactor = factors[state->dst_alpha];
        color.rgbBlendOperation = operations[state->rgb_operation]; color.alphaBlendOperation = operations[state->alpha_operation];
        color.writeMask = ((state->color_mask & 1) ? MTLColorWriteMaskRed : 0)
            | ((state->color_mask & 2) ? MTLColorWriteMaskGreen : 0) | ((state->color_mask & 4) ? MTLColorWriteMaskBlue : 0)
            | ((state->color_mask & 8) ? MTLColorWriteMaskAlpha : 0);
        ArcResidentPipeline *replacement = [ArcResidentPipeline new]; replacement->state = *state;
        replacement.pipeline = [scene.device newRenderPipelineStateWithDescriptor:descriptor error:&error];
        MTLDepthStencilDescriptor *depth = [MTLDepthStencilDescriptor new];
        depth.depthCompareFunction = (state->flags & ARC_PIPE_DEPTH_TEST) ? comparisons[state->depth_compare] : MTLCompareFunctionAlways;
        depth.depthWriteEnabled = (state->flags & ARC_PIPE_DEPTH_WRITE) != 0;
        replacement.depth = [scene.device newDepthStencilStateWithDescriptor:depth];
        if (!replacement.pipeline || !replacement.depth) {
            arc_release(scene); return arc_fail(scene, error.localizedDescription ?: @"Metal pass pipeline creation failed", ARC_FAILED);
        }
        scene.passPipelines[@(identifier)] = replacement;
        arc_release(scene); return ARC_OK;
    }
}

int arc_scene_pipeline_state_remove(void *pointer, int identifier) {
    ArcResidentScene *scene = arc_context(pointer);
    if (!scene || identifier < 1 || identifier > ARC_PIPELINE_LIMIT) return ARC_INVALID;
    if (!arc_acquire(scene)) return ARC_BUSY;
    [scene.passPipelines removeObjectForKey:@(identifier)]; arc_release(scene); return ARC_OK;
}

int arc_scene_submit_passes(void *pointer, int width, int height, const void *uniforms, uint64_t uniform_length,
    const arc_scene_pass *passes, int pass_count, const arc_scene_command *commands, int command_count) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene || !uniforms || uniform_length != scene.uniforms.length || (command_count && !commands)
            || (command_count > 0 && ((uintptr_t)commands & 3))) return ARC_INVALID;
        if (!arc_acquire(scene)) { scene.busy++; return ARC_BUSY; }
        uint32_t stored = 0;
        uint32_t previous = scene.color.width == (NSUInteger)width && scene.color.height == (NSUInteger)height ? scene.storedAttachments : 0;
        if (scene.commandFailed || !arc_scene_valid_passes(width, height, uniform_length, passes, pass_count, command_count, previous, &stored)) {
            arc_release(scene); return scene.commandFailed ? ARC_FAILED : ARC_INVALID;
        }
        for (int i = 0; i < pass_count; i++) {
            ArcResidentPipeline *pipeline = scene.passPipelines[@(passes[i].pipeline_id)];
            if (!pipeline || ((pipeline->state.flags & ARC_PIPE_DEPTH_TEST) && ((passes[i].load_actions >> 2) & 3) == ARC_DISCARD)) {
                arc_release(scene); return arc_fail(scene, @"Pass references a missing pipeline or undefined depth", ARC_INVALID);
            }
        }
        for (int i = 0; i < command_count; i++) {
            arc_scene_command draw = commands[i]; id<MTLBuffer> vertices = scene.meshes[@(draw.mesh_id)];
            if (!arc_id((int)draw.mesh_id) || !vertices || (draw.material_id && !scene.materials[@(draw.material_id)])
                || !arc_scene_valid_range(draw.first_vertex, draw.vertex_count, vertices.length / scene.vertexStride)
                || !arc_scene_valid_range(draw.first_actor, draw.actor_count, scene.actorCapacity) || draw.vertex_count % 3) {
                arc_release(scene); return arc_fail(scene, @"Pass draw references an invalid resource or range", ARC_INVALID);
            }
        }
        int result = arc_targets(scene, width, height);
        if (result != ARC_OK) { arc_release(scene); return result; }
        memcpy(scene.uniforms.contents, uniforms, (size_t)uniform_length);
        id<MTLCommandBuffer> command = [scene.queue commandBuffer];
        if (!command) { arc_release(scene); return ARC_FAILED; }
        uint64_t draws = 0;
        const MTLLoadAction loads[] = {MTLLoadActionLoad, MTLLoadActionClear, MTLLoadActionDontCare};
        const MTLCullMode culls[] = {MTLCullModeNone, MTLCullModeFront, MTLCullModeBack};
        for (int i = 0; i < pass_count; i++) {
            const arc_scene_pass *p = passes + i; ArcResidentPipeline *pipeline = scene.passPipelines[@(p->pipeline_id)];
            MTLRenderPassDescriptor *pass = [MTLRenderPassDescriptor new];
            pass.colorAttachments[0].texture = scene.color; pass.depthAttachment.texture = scene.depth;
            pass.colorAttachments[0].loadAction = loads[p->load_actions & 3];
            pass.depthAttachment.loadAction = loads[(p->load_actions >> 2) & 3];
            pass.colorAttachments[0].storeAction = (p->store_actions & ARC_ATTACH_COLOR) ? MTLStoreActionStore : MTLStoreActionDontCare;
            pass.depthAttachment.storeAction = (p->store_actions & ARC_ATTACH_DEPTH) ? MTLStoreActionStore : MTLStoreActionDontCare;
            pass.colorAttachments[0].clearColor = MTLClearColorMake(p->clear_rgba[0], p->clear_rgba[1], p->clear_rgba[2], p->clear_rgba[3]);
            pass.depthAttachment.clearDepth = p->clear_depth;
            id<MTLRenderCommandEncoder> encoder = [command renderCommandEncoderWithDescriptor:pass];
            if (!encoder) { arc_release(scene); return arc_fail(scene, @"Could not encode an ordered Metal pass", ARC_FAILED); }
            [encoder setRenderPipelineState:pipeline.pipeline]; [encoder setDepthStencilState:pipeline.depth];
            [encoder setCullMode:culls[pipeline->state.cull]];
            [encoder setFrontFacingWinding:pipeline->state.winding == ARC_WIND_CCW ? MTLWindingCounterClockwise : MTLWindingClockwise];
            [encoder setViewport:(MTLViewport){p->viewport_x, height - p->viewport_y - p->viewport_height,
                p->viewport_width, p->viewport_height, p->min_depth, p->max_depth}];
            MTLScissorRect scissor = {0, 0, (NSUInteger)width, (NSUInteger)height};
            if (p->flags & ARC_PASS_SCISSOR) scissor = (MTLScissorRect){p->scissor_x, height - p->scissor_y - p->scissor_height, p->scissor_width, p->scissor_height};
            [encoder setScissorRect:scissor];
            [encoder setVertexBuffer:scene.actors offset:0 atIndex:1];
            [encoder setVertexBuffer:scene.uniforms offset:p->uniform_offset atIndex:2];
            [encoder setFragmentBuffer:scene.uniforms offset:p->uniform_offset atIndex:2];
            for (uint32_t j = p->first_command; j < p->first_command + p->command_count; j++) {
                arc_scene_command draw = commands[j]; if (!draw.vertex_count || !draw.actor_count) continue;
                [encoder setVertexBuffer:scene.meshes[@(draw.mesh_id)] offset:0 atIndex:0];
                [encoder setFragmentTexture:draw.material_id ? scene.materials[@(draw.material_id)] : scene.white atIndex:0];
                [encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:draw.first_vertex vertexCount:draw.vertex_count
                    instanceCount:draw.actor_count baseInstance:draw.first_actor]; draws++;
            }
            [encoder endEncoding];
        }
        scene.submitted++; scene.storedAttachments = stored;
        atomic_store(&scene->frameInFlight, true);
        [command addCompletedHandler:^(id<MTLCommandBuffer> completed) {
            scene.completed++; scene.drawCalls += draws; scene.completedPasses += (uint64_t)pass_count;
            if (completed.status == MTLCommandBufferStatusError) {
                scene.commandFailed = YES; arc_fail(scene, completed.error.localizedDescription ?: @"Ordered Metal pass failed", ARC_FAILED);
            }
            atomic_store(&scene->frameInFlight, false); arc_release(scene);
        }];
        [command commit]; return ARC_OK;
    }
}

int arc_scene_read_depth(void *pointer, void *destination, uint64_t length) {
    @autoreleasepool {
        ArcResidentScene *scene = arc_context(pointer);
        if (!scene || !destination || !scene.depth || !(scene.storedAttachments & ARC_ATTACH_DEPTH)
            || length != (uint64_t)scene.depth.width * scene.depth.height * 4) return ARC_INVALID;
        int result = arc_scene_wait(pointer); if (result != ARC_OK) return result;
        if (!arc_acquire(scene)) return ARC_BUSY;
        NSUInteger stride = (scene.depth.width * 4 + 255) & ~(NSUInteger)255;
        if (arc_resident_bytes(scene) + stride * scene.depth.height > arc_resident_limit) { arc_release(scene); return ARC_INVALID; }
        id<MTLBuffer> bytes = [scene.device newBufferWithLength:stride * scene.depth.height options:MTLResourceStorageModeShared];
        id<MTLCommandBuffer> command = [scene.queue commandBuffer];
        id<MTLBlitCommandEncoder> blit = [command blitCommandEncoder];
        if (!bytes || !command || !blit) { arc_release(scene); return ARC_FAILED; }
        [blit copyFromTexture:scene.depth sourceSlice:0 sourceLevel:0 sourceOrigin:MTLOriginMake(0, 0, 0)
            sourceSize:MTLSizeMake(scene.depth.width, scene.depth.height, 1) toBuffer:bytes destinationOffset:0
            destinationBytesPerRow:stride destinationBytesPerImage:stride * scene.depth.height];
        [blit endEncoding]; dispatch_semaphore_t finished = dispatch_semaphore_create(0);
        [command addCompletedHandler:^(id<MTLCommandBuffer> completed) {
            if (completed.status == MTLCommandBufferStatusError) scene.commandFailed = YES;
            arc_release(scene); dispatch_semaphore_signal(finished);
        }];
        [command commit];
        if (dispatch_semaphore_wait(finished, dispatch_time(DISPATCH_TIME_NOW, INT64_C(5000000000))) != 0) {
            scene.commandFailed = YES; return arc_fail(scene, @"Depth readback timed out", ARC_FAILED);
        }
        if (!arc_acquire(scene)) return ARC_BUSY;
        if (scene.commandFailed) { arc_release(scene); return ARC_FAILED; }
        for (NSUInteger y = 0; y < scene.depth.height; y++)
            memcpy((uint8_t *)destination + y * scene.depth.width * 4, (uint8_t *)bytes.contents + y * stride, scene.depth.width * 4);
        arc_release(scene); return ARC_OK;
    }
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
            [command addCompletedHandler:^(id<MTLCommandBuffer> finished) {
                ArcResidentScene *retained = scene;
                if (finished.status == MTLCommandBufferStatusError) {
                    retained.commandFailed = YES;
                    arc_fail(retained, finished.error.localizedDescription ?: @"Metal readback failed", ARC_FAILED);
                }
                arc_release(retained);
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
        case 7: return scene.passPipelines.count;
        case 8: return scene.completedPasses;
        case 9: return scene.storedAttachments;
        case 10: return (uint64_t)atomic_load(&scene->deviceLease);
        case 11: return (uint64_t)atomic_load(&scene->targetLease);
        case 12: return scene.targetGeneration;
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
        if (scene.targetGeneration == UINT64_MAX) { arc_release(scene); return ARC_INVALID; }
        [scene.meshes removeAllObjects];
        [scene.materials removeAllObjects];
        [scene.passPipelines removeAllObjects];
        scene.meshBytes = scene.materialBytes = 0;
        scene.color = nil; scene.depth = nil; scene.storedAttachments = 0; scene.targetGeneration++;
        memset(scene.actors.contents, 0, scene.actors.length);
        memset(scene.uniforms.contents, 0, scene.uniforms.length);
        arc_release(scene); return ARC_OK;
    }
}

@interface ArcMetalLease : NSObject
@property(nonatomic, strong) ArcResidentScene *scene;
@property(nonatomic, strong) id<MTLDevice> device;
@property(nonatomic, strong) id<MTLCommandQueue> queue;
@property(nonatomic, strong) id<MTLTexture> texture;
@property(nonatomic) int kind;
@property(nonatomic) uint64_t frame, generation;
@property(nonatomic) BOOL released;
@end
@implementation ArcMetalLease
@end

int arc_scene_metal_lease_abi(void) { return 1; }
int arc_scene_metal_lease_acquire(void *pointer, int kind, uint64_t frame, void **out) {
    @autoreleasepool {
    if (!out) return ARC_INVALID;
    *out = NULL;
    ArcResidentScene *scene = arc_context(pointer);
    if (!scene || scene.externallyClosed || !pthread_equal(scene->ownerThread, pthread_self())
        || (kind != ARC_METAL_DEVICE_LEASE && kind != ARC_METAL_TARGET_LEASE)
        || (kind == ARC_METAL_DEVICE_LEASE && frame != 0)) return ARC_INVALID;
    atomic_int *flag = kind == ARC_METAL_DEVICE_LEASE ? &scene->deviceLease : &scene->targetLease;
    int expected = 0;
    if (!atomic_compare_exchange_strong(flag, &expected, 1)) return ARC_BUSY;
    BOOL held = NO;
    if (kind == ARC_METAL_TARGET_LEASE) {
        held = dispatch_semaphore_wait(scene.permit, DISPATCH_TIME_NOW) == 0;
        if (!held && !atomic_load(&scene->frameInFlight)) {
            atomic_store(flag, 0); return ARC_BUSY;
        }
        if (!frame || frame != scene.submitted || !(scene.storedAttachments & ARC_ATTACH_COLOR)
            || !scene.color || !scene.targetGeneration || scene.externallyClosed) {
            if (held) arc_release(scene);
            atomic_store(flag, 0); return ARC_INVALID;
        }
    }
    if (scene.externallyClosed) { if (held) arc_release(scene); atomic_store(flag, 0); return ARC_INVALID; }
    ArcMetalLease *lease = [ArcMetalLease new];
    if (!lease) { if (held) arc_release(scene); atomic_store(flag, 0); return ARC_FAILED; }
    lease.scene = scene; lease.device = scene.device; lease.queue = scene.queue; lease.kind = kind;
    if (kind == ARC_METAL_TARGET_LEASE) {
        lease.texture = scene.color; lease.generation = scene.targetGeneration; lease.frame = frame;
        dispatch_group_enter(scene.externalWork);
    }
    if (held) arc_release(scene);
    *out = (__bridge_retained void *)lease;
    return ARC_OK;
    }
}

int arc_scene_metal_lease_info(void *pointer, arc_scene_metal_info *out) {
    if (!out) return ARC_INVALID;
    memset(out, 0, sizeof(*out));
    ArcMetalLease *lease = (__bridge ArcMetalLease *)pointer;
    if (!lease || lease.released || lease.scene.externallyClosed
        || !pthread_equal(lease.scene->ownerThread, pthread_self())) return ARC_INVALID;
    if (lease.kind == ARC_METAL_TARGET_LEASE && (lease.frame != lease.scene.submitted
        || lease.generation != lease.scene.targetGeneration || lease.texture != lease.scene.color)) return ARC_INVALID;
    *out = (arc_scene_metal_info){lease.frame, lease.generation, (uint32_t)lease.texture.width, (uint32_t)lease.texture.height,
        (uint64_t)(uintptr_t)(__bridge void *)lease.device, (uint64_t)(uintptr_t)(__bridge void *)lease.queue,
        (uint64_t)(uintptr_t)(__bridge void *)lease.texture};
    return ARC_OK;
}

int arc_scene_metal_lease_release(void *pointer) {
    @autoreleasepool {
    ArcMetalLease *lease = (__bridge ArcMetalLease *)pointer;
    if (!lease || lease.released || !pthread_equal(lease.scene->ownerThread, pthread_self())) return ARC_INVALID;
    id<MTLCommandBuffer> fence = [lease.queue commandBuffer];
    if (!fence) return ARC_FAILED;
    ArcResidentScene *scene = lease.scene;
    atomic_int *flag = lease.kind == ARC_METAL_DEVICE_LEASE ? &scene->deviceLease : &scene->targetLease;
    if (lease.kind == ARC_METAL_DEVICE_LEASE) dispatch_group_enter(scene.externalWork);
    lease.released = YES; atomic_store(flag, 2);
    [fence addCompletedHandler:^(id<MTLCommandBuffer> completed) {
        if (completed.status == MTLCommandBufferStatusError) scene.commandFailed = YES;
        atomic_store(lease.kind == ARC_METAL_DEVICE_LEASE ? &scene->deviceLease : &scene->targetLease, 0);
        dispatch_group_leave(scene.externalWork);
    }];
    [fence commit]; (void)CFBridgingRelease(pointer);
    return ARC_OK;
    }
}
