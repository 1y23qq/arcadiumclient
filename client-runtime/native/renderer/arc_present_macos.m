// SPDX-License-Identifier: GPL-3.0-only
#define GL_SILENCE_DEPRECATION
#include "arc_present.h"
#include "arc_scene_metal_objects.h"
#import <Cocoa/Cocoa.h>
#import <CoreVideo/CoreVideo.h>
#import <IOSurface/IOSurface.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#import <QuartzCore/CATransaction.h>
#include <OpenGL/OpenGL.h>
#include <OpenGL/CGLIOSurface.h>
#include <OpenGL/gl.h>
#include <OpenGL/glext.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

static const uint64_t image_limit = UINT64_C(128) * 1024 * 1024;

@interface ArcPresentationState : NSObject {
@public atomic_uint_fast64_t completed, displayed, liveBytes;
@public atomic_bool inFlight, failed;
}
@end
@implementation ArcPresentationState
- (instancetype)init {
    if ((self = [super init])) {
        atomic_init(&completed, 0); atomic_init(&displayed, 0); atomic_init(&liveBytes, 0);
        atomic_init(&inFlight, false); atomic_init(&failed, false);
    }
    return self;
}
@end

@interface ArcPresentationImage : NSObject {
@public CVPixelBufferRef pixels;
@public CVMetalTextureRef metalView;
@public GLuint texture, framebuffer;
}
@property(nonatomic, strong) id<MTLTexture> metal;
@property(nonatomic, strong) ArcPresentationState *state;
@property(nonatomic) uint64_t bytes;
@property(nonatomic) uint32_t width, height;
@end
@implementation ArcPresentationImage
- (void)dealloc {
    if (metalView) CFRelease(metalView);
    if (pixels) CFRelease(pixels);
    if (_state && _bytes) atomic_fetch_sub(&_state->liveBytes, _bytes);
}
@end

@interface ArcPresentationView : NSView
@end
@implementation ArcPresentationView
- (CALayer *)makeBackingLayer { return [CAMetalLayer layer]; }
- (BOOL)isOpaque { return YES; }
- (NSView *)hitTest:(NSPoint)point { (void)point; return nil; }
@end

@interface ArcPresentation : NSObject {
@public char error[1024];
@public CGLContextObj context;
@public pthread_t renderThread;
@public CVMetalTextureCacheRef cache;
}
@property(nonatomic, weak) NSWindow *window;
@property(nonatomic, strong) ArcPresentationView *view;
@property(nonatomic, strong) id<MTLDevice> device;
@property(nonatomic, strong) id<MTLCommandQueue> queue;
@property(nonatomic, strong) id<MTLRenderPipelineState> pipeline;
@property(nonatomic, strong) ArcPresentationImage *image;
@property(nonatomic, strong) ArcPresentationState *state;
@property(nonatomic, strong) NSLock *errorLock;
@property(nonatomic) uint64_t prepared, submitted, fallbacks;
@property(atomic) BOOL hidden, attached, glReleased;
@property(nonatomic) BOOL ticket;
@property(nonatomic) GLint preparedSwapInterval;
#if defined(ARC_PRESENT_TESTING)
@property(nonatomic, strong) id<MTLBuffer> testingPixels;
@property(nonatomic) NSUInteger testingStride;
#endif
@end
@implementation ArcPresentation
- (void)dealloc {
    if (cache) CFRelease(cache);
    if (context) CGLReleaseContext(context);
}
@end

static ArcPresentation *bridge(void *value) { return (__bridge ArcPresentation *)value; }
static int failure(ArcPresentation *p, NSString *message, int code) {
    if (p) {
        [p.errorLock lock]; snprintf(p->error, sizeof(p->error), "%s", message.UTF8String ?: "Metal presentation failure"); [p.errorLock unlock];
    }
    return code;
}
static BOOL mainThread(void) { return NSThread.isMainThread; }
static void latest(atomic_uint_fast64_t *target, uint64_t value) {
    uint_fast64_t previous = atomic_load(target);
    while (previous < value && !atomic_compare_exchange_weak(target, &previous, value)) { }
}
static BOOL renderContext(ArcPresentation *p) {
    return p && p->context && pthread_equal(p->renderThread, pthread_self()) && CGLGetCurrentContext() == p->context;
}
static void hideView(ArcPresentation *p) {
    p.view.hidden = YES; p.hidden = YES; p.ticket = NO; p.fallbacks++;
}
static void deleteImageGl(ArcPresentationImage *image) {
    if (image->framebuffer) glDeleteFramebuffersEXT(1, &image->framebuffer);
    if (image->texture) glDeleteTextures(1, &image->texture);
    image->framebuffer = image->texture = 0;
}

int arc_present_abi(void) { return 1; }
int arc_present_error(void *value, char *out, uint64_t capacity) {
    if (!out || capacity == 0) return ARC_PRESENT_INVALID;
    ArcPresentation *p = bridge(value);
    if (!p) { snprintf(out, (size_t)MIN(capacity, UINT64_C(1024)), "No Metal presentation context"); return ARC_PRESENT_OK; }
    [p.errorLock lock]; snprintf(out, (size_t)MIN(capacity, UINT64_C(1024)), "%s", p->error); [p.errorLock unlock];
    return ARC_PRESENT_OK;
}

int arc_present_create(void *scene, void *windowPointer, void **out) {
    if (!out) return ARC_PRESENT_INVALID;
    *out = NULL;
    if (!mainThread() || !scene || !windowPointer) return ARC_PRESENT_INVALID;
    @autoreleasepool {
        NSWindow *window = (__bridge NSWindow *)windowPointer;
        if (![window isKindOfClass:NSWindow.class] || !window.contentView || !window.opaque) return ARC_PRESENT_UNAVAILABLE;
        id<MTLDevice> device = nil; id<MTLCommandQueue> queue = nil;
        if (arc_scene_metal_objects(scene, &device, &queue) != ARC_OK) return ARC_PRESENT_UNAVAILABLE;
        ArcPresentation *p = [ArcPresentation new]; p.device = device; p.queue = queue;
        p.state = [ArcPresentationState new]; p.errorLock = [NSLock new]; p.hidden = YES;
        @try {
            NSError *error = nil;
            NSString *source = @"#include <metal_stdlib>\nusing namespace metal;\n"
                "vertex float4 arc_present_vertex(uint n [[vertex_id]]) { const float2 p[3]={float2(-1,-1),float2(3,-1),float2(-1,3)}; return float4(p[n],0,1); }\n"
                "fragment float4 arc_present_pixel(float4 p [[position]], texture2d<float,access::read> image [[texture(0)]]) { uint2 xy=uint2(p.xy); return float4(image.read(uint2(xy.x,image.get_height()-1-xy.y)).rgb,1); }\n";
            id<MTLLibrary> library = [device newLibraryWithSource:source options:nil error:&error];
            if (!library) return ARC_PRESENT_UNAVAILABLE;
            MTLRenderPipelineDescriptor *descriptor = [MTLRenderPipelineDescriptor new];
            descriptor.vertexFunction = [library newFunctionWithName:@"arc_present_vertex"];
            descriptor.fragmentFunction = [library newFunctionWithName:@"arc_present_pixel"];
            descriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
            p.pipeline = [device newRenderPipelineStateWithDescriptor:descriptor error:&error];
            if (!p.pipeline || CVMetalTextureCacheCreate(kCFAllocatorDefault, NULL, device, NULL, &p->cache) != kCVReturnSuccess)
                return ARC_PRESENT_UNAVAILABLE;
            p.view = [[ArcPresentationView alloc] initWithFrame:window.contentView.bounds];
            p.view.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
            p.view.wantsLayer = YES; p.view.hidden = YES;
            CAMetalLayer *layer = (CAMetalLayer *)p.view.layer;
            layer.device = device; layer.pixelFormat = MTLPixelFormatBGRA8Unorm;
            layer.presentsWithTransaction = YES;
            layer.framebufferOnly = YES; layer.opaque = YES;
#if defined(ARC_PRESENT_TESTING)
            layer.framebufferOnly = NO;
#endif
            layer.maximumDrawableCount = 2; layer.allowsNextDrawableTimeout = YES;
            p.window = window;
            [window.contentView addSubview:p.view]; p.attached = YES;
            *out = (__bridge_retained void *)p;
            return ARC_PRESENT_OK;
        } @catch (NSException *exception) {
            (void)exception;
            [p.view removeFromSuperview]; p.attached = NO; p.view = nil; p.window = nil;
            return ARC_PRESENT_FAILED;
        }
    }
}

static ArcPresentationImage *makeImage(ArcPresentation *p, uint32_t width, uint32_t height) {
    ArcPresentationImage *image = [ArcPresentationImage new]; image.width = width; image.height = height;
    NSDictionary *attributes = @{(__bridge id)kCVPixelBufferMetalCompatibilityKey:@YES,
        (__bridge id)kCVPixelBufferOpenGLCompatibilityKey:@YES, (__bridge id)kCVPixelBufferIOSurfacePropertiesKey:@{}};
    if (CVPixelBufferCreate(kCFAllocatorDefault, width, height, kCVPixelFormatType_32BGRA,
            (__bridge CFDictionaryRef)attributes, &image->pixels) != kCVReturnSuccess) return nil;
    IOSurfaceRef surface = CVPixelBufferGetIOSurface(image->pixels);
    uint64_t bytes = surface ? IOSurfaceGetAllocSize(surface) : 0;
    if (!bytes || bytes > image_limit || atomic_load(&p.state->liveBytes) + bytes > image_limit * 2) return nil;
    image.state = p.state; image.bytes = bytes; atomic_fetch_add(&p.state->liveBytes, bytes);
    if (CVMetalTextureCacheCreateTextureFromImage(kCFAllocatorDefault, p->cache, image->pixels, NULL,
            MTLPixelFormatBGRA8Unorm, width, height, 0, &image->metalView) != kCVReturnSuccess) return nil;
    image.metal = CVMetalTextureGetTexture(image->metalView);
    if (!image.metal) return nil;
    glGenTextures(1, &image->texture); glBindTexture(GL_TEXTURE_RECTANGLE_ARB, image->texture);
    if (CGLTexImageIOSurface2D(p->context, GL_TEXTURE_RECTANGLE_ARB, GL_RGBA8, width, height,
            GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, surface, 0) != kCGLNoError) { deleteImageGl(image); return nil; }
    glTexParameteri(GL_TEXTURE_RECTANGLE_ARB, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_RECTANGLE_ARB, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glGenFramebuffersEXT(1, &image->framebuffer); glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER_EXT, image->framebuffer);
    glFramebufferTexture2DEXT(GL_DRAW_FRAMEBUFFER_EXT, GL_COLOR_ATTACHMENT0_EXT, GL_TEXTURE_RECTANGLE_ARB, image->texture, 0);
    if (glCheckFramebufferStatusEXT(GL_DRAW_FRAMEBUFFER_EXT) != GL_FRAMEBUFFER_COMPLETE_EXT) { deleteImageGl(image); return nil; }
    return image;
}

int arc_present_prepare(void *value, const arc_present_frame *frame) {
    uint64_t bytes;
    ArcPresentation *p = bridge(value);
    if (!p || !arc_present_valid_frame(frame, &bytes)) return ARC_PRESENT_INVALID;
    (void)bytes;
    if (p.glReleased || frame->frame_id <= p.prepared) return failure(p, @"Invalid presentation frame ownership", ARC_PRESENT_INVALID);
    if (frame->color_encoding != ARC_PRESENT_RGBA8_LINEAR) return failure(p, @"sRGB transport has not been qualified", ARC_PRESENT_UNAVAILABLE);
    if (atomic_load(&p.state->inFlight)) return ARC_PRESENT_BUSY;
    if (atomic_load(&p.state->failed)) return failure(p, @"A Metal command failed", ARC_PRESENT_FAILED);
    p.ticket = NO;
    if (!p->context) {
        CGLContextObj current = CGLGetCurrentContext();
        if (!current) return failure(p, @"No current original OpenGL context", ARC_PRESENT_UNAVAILABLE);
        p->context = CGLRetainContext(current); p->renderThread = pthread_self();
    }
    if (!renderContext(p)) return failure(p, @"Presentation called with a different GL context or thread", ARC_PRESENT_INVALID);
    GLint swapInterval = 0;
    if (CGLGetParameter(p->context, kCGLCPSwapInterval, &swapInterval) != kCGLNoError || (swapInterval != 0 && swapInterval != 1))
        return failure(p, @"The original GL swap interval is not qualified", ARC_PRESENT_UNAVAILABLE);
    const char *extensions = (const char *)glGetString(GL_EXTENSIONS);
    if (!extensions || !strstr(extensions, "GL_EXT_framebuffer_blit") || !strstr(extensions, "GL_EXT_framebuffer_object"))
        return failure(p, @"Compatible GL framebuffer blit is unavailable", ARC_PRESENT_UNAVAILABLE);
    GLint active, rectangle, read, draw, defaultReadBuffer = GL_BACK;
    glGetIntegerv(GL_ACTIVE_TEXTURE, &active); glActiveTexture(GL_TEXTURE0);
    glGetIntegerv(GL_TEXTURE_BINDING_RECTANGLE_ARB, &rectangle);
    glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING_EXT, &read); glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING_EXT, &draw);
    GLboolean scissor = glIsEnabled(GL_SCISSOR_TEST);
    int status = ARC_PRESENT_OK;
    @try {
        glBindFramebufferEXT(GL_READ_FRAMEBUFFER_EXT, 0); glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER_EXT, 0);
        glGetIntegerv(GL_READ_BUFFER, &defaultReadBuffer);
        GLint samples = 0, red = 0, green = 0, blue = 0, alpha = 0;
        glGetIntegerv(GL_SAMPLES, &samples); glGetIntegerv(GL_RED_BITS, &red); glGetIntegerv(GL_GREEN_BITS, &green);
        glGetIntegerv(GL_BLUE_BITS, &blue); glGetIntegerv(GL_ALPHA_BITS, &alpha);
        BOOL srgb = strstr(extensions, "GL_EXT_framebuffer_sRGB") && glIsEnabled(GL_FRAMEBUFFER_SRGB_EXT);
        if (samples != 0 || red != 8 || green != 8 || blue != 8 || (alpha != 0 && alpha != 8) || srgb)
            return failure(p, @"The default drawable format is not qualified", ARC_PRESENT_UNAVAILABLE);
        if (p.image && (p.image.width != frame->width || p.image.height != frame->height)) { deleteImageGl(p.image); p.image = nil; CVMetalTextureCacheFlush(p->cache, 0); }
        if (!p.image) p.image = makeImage(p, frame->width, frame->height);
        if (!p.image) status = failure(p, @"Could not allocate a bounded GL/Metal shared image", ARC_PRESENT_UNAVAILABLE);
        else {
            glBindFramebufferEXT(GL_READ_FRAMEBUFFER_EXT, 0); glReadBuffer(GL_BACK);
            glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER_EXT, p.image->framebuffer); glDrawBuffer(GL_COLOR_ATTACHMENT0_EXT);
            glDisable(GL_SCISSOR_TEST);
            glBlitFramebufferEXT(0, 0, frame->width, frame->height, 0, 0, frame->width, frame->height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            // Current-frame correctness prototype; synchronization is not a performance result.
            glFinish();
            p.prepared = frame->frame_id; p.preparedSwapInterval = swapInterval; p.ticket = YES;
        }
    } @catch (NSException *exception) { status = failure(p, exception.reason, ARC_PRESENT_FAILED); }
    @finally {
        glBindFramebufferEXT(GL_READ_FRAMEBUFFER_EXT, 0); glReadBuffer(defaultReadBuffer);
        glBindFramebufferEXT(GL_READ_FRAMEBUFFER_EXT, read); glBindFramebufferEXT(GL_DRAW_FRAMEBUFFER_EXT, draw);
        if (scissor) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
        glBindTexture(GL_TEXTURE_RECTANGLE_ARB, rectangle); glActiveTexture(active);
    }
    return status;
}

int arc_present_publish(void *value, uint64_t frame) {
    ArcPresentation *p = bridge(value);
    if (!p || !mainThread()) return ARC_PRESENT_INVALID;
    if (!p.ticket || frame != p.prepared || p.glReleased || !p.window || !p.attached || !p.image
            || atomic_load(&p.state->inFlight) || atomic_load(&p.state->failed)) {
        hideView(p); return failure(p, @"No current prepared frame can be published", ARC_PRESENT_BUSY);
    }
    @autoreleasepool {
        BOOL submitted = NO;
        @try {
            NSView *parent = p.window.contentView;
            if (!parent || p.view.superview != parent || ![p.view.layer isKindOfClass:CAMetalLayer.class]) {
                hideView(p); return failure(p, @"The owned Metal view is detached from its original window", ARC_PRESENT_UNAVAILABLE);
            }
            CAMetalLayer *layer = (CAMetalLayer *)p.view.layer;
            [CATransaction begin];
            @try {
                [CATransaction setDisableActions:YES];
                [parent layoutSubtreeIfNeeded];
                NSRect bounds = parent.bounds;
                double sx = p.image.width / bounds.size.width, sy = p.image.height / bounds.size.height;
                if (!isfinite(bounds.size.width) || !isfinite(bounds.size.height) || bounds.size.width <= 0 || bounds.size.height <= 0
                        || !isfinite(sx) || !isfinite(sy) || sx <= 0 || sy <= 0
                        || fabs(sx - sy) > 1.0 / fmax(bounds.size.width, bounds.size.height)) {
                    hideView(p); return failure(p, @"The current window and framebuffer sizes are inconsistent", ARC_PRESENT_UNAVAILABLE);
                }
                p.view.frame = bounds; [p.view layoutSubtreeIfNeeded];
                layer.displaySyncEnabled = p.preparedSwapInterval != 0;
                layer.contentsScale = sy; layer.drawableSize = CGSizeMake(p.image.width, p.image.height);
                p.view.hidden = NO; p.hidden = NO;
                id<CAMetalDrawable> drawable = [layer nextDrawable];
                if (!drawable || drawable.texture.width != p.image.width || drawable.texture.height != p.image.height) {
                    hideView(p); return failure(p, @"No correctly sized current Metal drawable", ARC_PRESENT_UNAVAILABLE);
                }
                MTLRenderPassDescriptor *pass = [MTLRenderPassDescriptor renderPassDescriptor];
                pass.colorAttachments[0].texture = drawable.texture;
                pass.colorAttachments[0].loadAction = MTLLoadActionDontCare; pass.colorAttachments[0].storeAction = MTLStoreActionStore;
                id<MTLCommandBuffer> command = [p.queue commandBuffer];
                id<MTLRenderCommandEncoder> encoder = [command renderCommandEncoderWithDescriptor:pass];
                if (!command || !encoder) { hideView(p); return failure(p, @"Could not encode current Metal presentation", ARC_PRESENT_FAILED); }
                [encoder setRenderPipelineState:p.pipeline]; [encoder setFragmentTexture:p.image.metal atIndex:0];
                [encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3]; [encoder endEncoding];
#if defined(ARC_PRESENT_TESTING)
                NSUInteger stride = ((NSUInteger)p.image.width * 4 + 255) & ~(NSUInteger)255;
                id<MTLBuffer> pixels = [p.device newBufferWithLength:stride * p.image.height options:MTLResourceStorageModeShared];
                id<MTLBlitCommandEncoder> readback = [command blitCommandEncoder];
                if (!pixels || !readback) { hideView(p); return failure(p, @"Could not encode presentation test readback", ARC_PRESENT_FAILED); }
                [readback copyFromTexture:pass.colorAttachments[0].texture sourceSlice:0 sourceLevel:0 sourceOrigin:MTLOriginMake(0, 0, 0)
                    sourceSize:MTLSizeMake(p.image.width, p.image.height, 1) toBuffer:pixels destinationOffset:0
                    destinationBytesPerRow:stride destinationBytesPerImage:stride * p.image.height];
                [readback endEncoding]; p.testingPixels = pixels; p.testingStride = stride;
#endif
                ArcPresentationState *state = p.state; ArcPresentationImage *ownedImage = p.image;
                atomic_store(&state->inFlight, true); p.submitted = frame; p.ticket = NO;
                [drawable addPresentedHandler:^(id<MTLDrawable> shown) { (void)shown; latest(&state->displayed, frame); }];
                [command addCompletedHandler:^(id<MTLCommandBuffer> finished) {
                    if (!ownedImage.metal || ownedImage.width == 0 || ownedImage.height == 0) atomic_store(&state->failed, true);
                    if (finished.status == MTLCommandBufferStatusError) atomic_store(&state->failed, true);
                    latest(&state->completed, frame); atomic_store(&state->inFlight, false);
                }];
                [command commit]; submitted = YES;
                [command waitUntilScheduled];
                if (command.status == MTLCommandBufferStatusError) {
                    hideView(p); return failure(p, @"The current Metal frame could not be scheduled", ARC_PRESENT_FAILED);
                }
                [drawable present];
                return ARC_PRESENT_OK;
            } @finally { [CATransaction commit]; [CATransaction flush]; }
        } @catch (NSException *exception) {
            if (!submitted) atomic_store(&p.state->inFlight, false);
            hideView(p); return failure(p, exception.reason, ARC_PRESENT_FAILED);
        }
    }
}

int arc_present_hide(void *value) {
    if (!value) return ARC_PRESENT_OK;
    if (!mainThread()) return ARC_PRESENT_INVALID;
    hideView(bridge(value)); [CATransaction flush]; return ARC_PRESENT_OK;
}
int arc_present_release_gl(void *value) {
    if (!value) return ARC_PRESENT_OK;
    ArcPresentation *p = bridge(value);
    if (p.glReleased) return ARC_PRESENT_OK;
    if (p->context && !renderContext(p)) return failure(p, @"GL release requires the original current context/thread", ARC_PRESENT_INVALID);
    if (p.image) { deleteImageGl(p.image); p.image = nil; CVMetalTextureCacheFlush(p->cache, 0); }
    p.ticket = NO; p.glReleased = YES;
    return ARC_PRESENT_OK;
}
int arc_present_destroy(void *value) {
    if (!value) return ARC_PRESENT_OK;
    ArcPresentation *p = bridge(value);
    if (!mainThread() || !p.glReleased) return ARC_PRESENT_INVALID;
    hideView(p); [p.view removeFromSuperview]; p.attached = NO; p.view = nil; p.window = nil;
    [CATransaction flush];
    (void)CFBridgingRelease(value);
    return ARC_PRESENT_OK;
}
uint64_t arc_present_query(void *value, int key) {
    ArcPresentation *p = bridge(value);
    if (!p) return 0;
    if (key >= ARC_PRESENT_PARENT_WIDTH_FP64) {
        if (!mainThread()) return 0;
        NSView *parent = p.window.contentView; CAMetalLayer *layer = (CAMetalLayer *)p.view.layer;
        double number = 0; uint64_t bits;
        switch (key) {
            case ARC_PRESENT_PARENT_WIDTH_FP64: number = parent.bounds.size.width; break;
            case ARC_PRESENT_PARENT_HEIGHT_FP64: number = parent.bounds.size.height; break;
            case ARC_PRESENT_VIEW_WIDTH_FP64: number = p.view.bounds.size.width; break;
            case ARC_PRESENT_VIEW_HEIGHT_FP64: number = p.view.bounds.size.height; break;
            case ARC_PRESENT_VIEW_FRAME_WIDTH_FP64: number = p.view.frame.size.width; break;
            case ARC_PRESENT_VIEW_FRAME_HEIGHT_FP64: number = p.view.frame.size.height; break;
            case ARC_PRESENT_DRAWABLE_WIDTH: return (uint64_t)layer.drawableSize.width;
            case ARC_PRESENT_DRAWABLE_HEIGHT: return (uint64_t)layer.drawableSize.height;
            case ARC_PRESENT_SCALE_FP64: number = layer.contentsScale; break;
            case ARC_PRESENT_WINDOW_SCALE_FP64: number = p.window.backingScaleFactor; break;
            case ARC_PRESENT_PARENT_ATTACHED: return parent && p.view.superview == parent;
            case ARC_PRESENT_WINDOW_VISIBLE: return p.window.isVisible;
            case ARC_PRESENT_WINDOW_OCCLUSION_VISIBLE: return (p.window.occlusionState & NSWindowOcclusionStateVisible) != 0;
            case ARC_PRESENT_WITH_TRANSACTION: return layer.presentsWithTransaction;
            default: return 0;
        }
        memcpy(&bits, &number, sizeof(bits)); return bits;
    }
    switch (key) {
        case ARC_PRESENT_LAST_PREPARED: return p.prepared;
        case ARC_PRESENT_LAST_SUBMITTED: return p.submitted;
        case ARC_PRESENT_LAST_COMPLETED: return atomic_load(&p.state->completed);
        case ARC_PRESENT_LAST_DISPLAYED: return atomic_load(&p.state->displayed);
        case ARC_PRESENT_LIVE_BYTES: return atomic_load(&p.state->liveBytes);
        case ARC_PRESENT_ATTACHED: return p.attached;
        case ARC_PRESENT_HIDDEN: return p.hidden;
        case ARC_PRESENT_GL_RELEASED: return p.glReleased;
        case ARC_PRESENT_IN_FLIGHT: return atomic_load(&p.state->inFlight);
        case ARC_PRESENT_FALLBACKS: return p.fallbacks;
        default: return 0;
    }
}
