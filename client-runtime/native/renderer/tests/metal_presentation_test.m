// SPDX-License-Identifier: GPL-3.0-only
#define ARC_PRESENT_TESTING 1
#include "../arc_present_macos.m"
#import <QuartzCore/CATransaction.h>
#include <stdlib.h>

#define CHECK(x) do { if (!(x)) { fprintf(stderr, "CHECK failed at %d: %s\n", __LINE__, #x); return 1; } } while (0)
static void pump(void) {
    NSDate *limit=[NSDate dateWithTimeIntervalSinceNow:.01];NSEvent *event;
    while((event=[NSApp nextEventMatchingMask:NSEventMaskAny untilDate:limit inMode:NSDefaultRunLoopMode dequeue:YES]))[NSApp sendEvent:event];
    [NSApp updateWindows];[CATransaction flush];
}
static void presentationState(ArcPresentation *p, uint64_t frame, const char *stage) {
    NSWindow *window=p.window;CAMetalLayer *layer=(CAMetalLayer *)p.view.layer;
    fprintf(stderr,"presentation stage=%s frame=%llu prepared=%llu submitted=%llu completed=%llu displayed=%llu inFlight=%d failed=%d visible=%d occlusionVisible=%d appActive=%d appHidden=%d key=%d hidden=%d ancestorHidden=%d attached=%d layerHidden=%d drawable=%.0fx%.0f scale=%.3f bytes=%llu\n",
        stage,(unsigned long long)frame,(unsigned long long)p.prepared,(unsigned long long)p.submitted,
        (unsigned long long)atomic_load(&p.state->completed),(unsigned long long)atomic_load(&p.state->displayed),
        atomic_load(&p.state->inFlight),atomic_load(&p.state->failed),window.isVisible,
        (window.occlusionState&NSWindowOcclusionStateVisible)!=0,NSApp.isActive,NSApp.isHidden,window.isKeyWindow,
        p.hidden,p.view.isHiddenOrHasHiddenAncestor,p.attached,layer.hidden,
        layer.drawableSize.width,layer.drawableSize.height,layer.contentsScale,(unsigned long long)atomic_load(&p.state->liveBytes));
}
static void pattern(int width, int height, int frame) {
    const float colors[4][3] = {{1,0,0},{0,1,0},{0,0,1},{1,1,0}};
    glViewport(0,0,width,height); glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_DITHER); glEnable(GL_SCISSOR_TEST);
    for (int corner=0;corner<4;corner++) {
        int value=(corner+frame)%4;
        glScissor((corner&1)*width/2,(corner>>1)*height/2,width/2,height/2);
        glClearColor(colors[value][0],colors[value][1],colors[value][2],1);glClear(GL_COLOR_BUFFER_BIT);
    }
}
static int checkImage(ArcPresentation *p, const uint8_t *source, int width, int height) {
    NSUInteger stride=p.testingStride;
    CHECK(p.testingPixels&&stride>=(NSUInteger)width*4&&p.testingPixels.length>=stride*height);
    const uint8_t *pixels=p.testingPixels.contents;
    for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
        const uint8_t *actual=pixels+y*stride+x*4,*expected=source+((height-1-y)*width+x)*4;
        CHECK(actual[0]==expected[2]&&actual[1]==expected[1]&&actual[2]==expected[0]&&actual[3]==255);
    }
    return 0;
}

int main(void) {
    @autoreleasepool {
        [NSApplication sharedApplication];CHECK([NSApp setActivationPolicy:NSApplicationActivationPolicyRegular]);[NSApp finishLaunching];
        void *scene=arc_scene_create(2,80,20,64);
        if(!scene){fprintf(stderr,"SKIP: %s\n",arc_scene_error(NULL));return 77;}
        NSOpenGLPixelFormatAttribute attributes[]={NSOpenGLPFADoubleBuffer,NSOpenGLPFAColorSize,24,
            NSOpenGLPFAAlphaSize,8,NSOpenGLPFADepthSize,24,0};
        NSOpenGLPixelFormat *format=[[NSOpenGLPixelFormat alloc]initWithAttributes:attributes];
        if(!format){arc_scene_destroy(scene);fprintf(stderr,"SKIP: no legacy GL pixel format\n");return 77;}
        NSWindow *window=[[NSWindow alloc]initWithContentRect:NSMakeRect(40,40,64,64)
            styleMask:NSWindowStyleMaskTitled|NSWindowStyleMaskResizable backing:NSBackingStoreBuffered defer:NO];
        NSOpenGLView *gl=[[NSOpenGLView alloc]initWithFrame:NSMakeRect(0,0,64,64) pixelFormat:format];
        window.releasedWhenClosed=NO;window.contentView=gl;window.opaque=YES;
        [window makeKeyAndOrderFront:nil];[NSApp activateIgnoringOtherApps:YES];pump();
        [gl.openGLContext makeCurrentContext];[gl.openGLContext update];
        NSSize pixels=[gl convertSizeToBacking:gl.bounds.size];int width=(int)pixels.width,height=(int)pixels.height;
        CHECK(width>0&&height>0&&(width&1)==0&&(height&1)==0);
        GLint interval=0;[gl.openGLContext setValues:&interval forParameter:NSOpenGLCPSwapInterval];
        NSUInteger children=gl.subviews.count;
        void *out=(void *)(uintptr_t)9;
        CHECK(arc_present_create(scene,NULL,&out)==ARC_PRESENT_INVALID&&out==NULL&&gl.subviews.count==children);
        CHECK(arc_present_create(scene,(__bridge void *)window,&out)==ARC_PRESENT_OK&&out);
        __unsafe_unretained ArcPresentation *p=bridge(out);
        CHECK(gl.subviews.count==children+1&&arc_present_query(out,ARC_PRESENT_HIDDEN)==1);
        CHECK([p.view hitTest:NSMakePoint(1,1)]==nil);
        uint8_t *source=NULL;
        GLuint sentinel;glGenTextures(1,&sentinel);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_RECTANGLE_ARB,sentinel);glActiveTexture(GL_TEXTURE1);
        GLuint sentinelFbo,attachments[2];glGenTextures(2,attachments);glGenFramebuffersEXT(1,&sentinelFbo);
        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT,sentinelFbo);
        const GLenum targets[2]={GL_COLOR_ATTACHMENT0_EXT,GL_COLOR_ATTACHMENT1_EXT};
        for(int i=0;i<2;i++) {
            glBindTexture(GL_TEXTURE_2D,attachments[i]);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,NULL);
            glFramebufferTexture2DEXT(GL_FRAMEBUFFER_EXT,targets[i],GL_TEXTURE_2D,attachments[i],0);
        }
        glDrawBuffers(2,targets);glReadBuffer(GL_COLOR_ATTACHMENT1_EXT);
        CHECK(glCheckFramebufferStatusEXT(GL_FRAMEBUFFER_EXT)==GL_FRAMEBUFFER_COMPLETE_EXT);
        glBindFramebufferEXT(GL_FRAMEBUFFER_EXT,0);
        for(uint64_t frame=1;frame<=3;frame++) {
            if(frame==3) {
                [window setContentSize:NSMakeSize(96,80)];pump();[gl.openGLContext update];
                pixels=[gl convertSizeToBacking:gl.bounds.size];width=(int)pixels.width;height=(int)pixels.height;
                CHECK(width>0&&height>0&&(width&1)==0&&(height&1)==0);
            }
            source=realloc(source,(size_t)width*height*4);CHECK(source);
            interval=frame==2?1:0;[gl.openGLContext setValues:&interval forParameter:NSOpenGLCPSwapInterval];
            CHECK(arc_present_set_swap_interval(out,interval)==ARC_PRESENT_OK);
            pattern(width,height,(int)frame);glReadBuffer(GL_BACK);glPixelStorei(GL_PACK_ALIGNMENT,1);glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,source);
            glReadBuffer(GL_FRONT);glBindFramebufferEXT(GL_FRAMEBUFFER_EXT,sentinelFbo);
            arc_present_frame descriptor={frame,(uint32_t)width,(uint32_t)height,0,0x0405,ARC_PRESENT_RGBA8_LINEAR,0};
            CHECK(arc_present_prepare(out,&descriptor)==ARC_PRESENT_OK);
            GLint active=0,rectangle=0;glGetIntegerv(GL_ACTIVE_TEXTURE,&active);CHECK(active==GL_TEXTURE1&&glIsEnabled(GL_SCISSOR_TEST));
            glActiveTexture(GL_TEXTURE0);glGetIntegerv(GL_TEXTURE_BINDING_RECTANGLE_ARB,&rectangle);CHECK((GLuint)rectangle==sentinel);glActiveTexture(active);
            GLint readFbo=0,drawFbo=0,readTarget=0,draw0=0,draw1=0;
            glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING_EXT,&readFbo);glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING_EXT,&drawFbo);
            glGetIntegerv(GL_READ_BUFFER,&readTarget);glGetIntegerv(GL_DRAW_BUFFER0,&draw0);glGetIntegerv(GL_DRAW_BUFFER1,&draw1);
            CHECK((GLuint)readFbo==sentinelFbo&&(GLuint)drawFbo==sentinelFbo&&readTarget==GL_COLOR_ATTACHMENT1_EXT);
            CHECK(draw0==GL_COLOR_ATTACHMENT0_EXT&&draw1==GL_COLOR_ATTACHMENT1_EXT);
            glBindFramebufferEXT(GL_FRAMEBUFFER_EXT,0);glGetIntegerv(GL_READ_BUFFER,&readTarget);CHECK(readTarget==GL_FRONT);
            CHECK(arc_present_publish(out,frame)==ARC_PRESENT_OK);
            CHECK(arc_present_query(out,ARC_PRESENT_WITH_TRANSACTION)==1);
            presentationState(p,frame,"submitted");
            CHECK(((CAMetalLayer *)p.view.layer).displaySyncEnabled==(interval!=0));
            CHECK(arc_present_query(out,ARC_PRESENT_CONFIGURED_SWAP_INTERVAL)==(uint64_t)interval);
            CHECK(arc_present_query(out,ARC_PRESENT_PREPARED_SWAP_INTERVAL)==(uint64_t)interval);
            NSDate *limit=[NSDate dateWithTimeIntervalSinceNow:5];
            while((arc_present_query(out,ARC_PRESENT_LAST_COMPLETED)!=frame||arc_present_query(out,ARC_PRESENT_LAST_DISPLAYED)!=frame)&&limit.timeIntervalSinceNow>0)pump();
            presentationState(p,frame,"wait-ended");
            CHECK(arc_present_query(out,ARC_PRESENT_LAST_PREPARED)==frame&&arc_present_query(out,ARC_PRESENT_LAST_SUBMITTED)==frame);
            CHECK(arc_present_query(out,ARC_PRESENT_LAST_COMPLETED)==frame&&arc_present_query(out,ARC_PRESENT_LAST_DISPLAYED)==frame);
            CHECK(checkImage(p,source,width,height)==0);
            CHECK(glGetError()==GL_NO_ERROR);
        }
        CHECK(arc_present_publish(out,2)==ARC_PRESENT_BUSY&&arc_present_query(out,ARC_PRESENT_HIDDEN)==1);
        CHECK(arc_present_set_swap_interval(out,-1)==ARC_PRESENT_INVALID);
        CHECK(arc_present_set_swap_interval(out,2)==ARC_PRESENT_INVALID);
        __block int offMain=ARC_PRESENT_OK,offMainPacing=ARC_PRESENT_OK;
        __block BOOL workerWasMain=YES;
        dispatch_semaphore_t workerDone=dispatch_semaphore_create(0);
        NSThread *worker=[[NSThread alloc]initWithBlock:^{
            workerWasMain=NSThread.isMainThread;offMain=arc_present_hide(out);offMainPacing=arc_present_set_swap_interval(out,1);dispatch_semaphore_signal(workerDone);
        }];
        [worker start];
        CHECK(dispatch_semaphore_wait(workerDone,dispatch_time(DISPATCH_TIME_NOW,INT64_C(5000000000)))==0);
        CHECK(!workerWasMain&&offMain==ARC_PRESENT_INVALID);
        CHECK(offMainPacing==ARC_PRESENT_INVALID);
        CHECK(arc_present_hide(out)==ARC_PRESENT_OK&&arc_present_query(out,ARC_PRESENT_HIDDEN)==1);
        CHECK(arc_present_release_gl(out)==ARC_PRESENT_OK&&arc_present_query(out,ARC_PRESENT_GL_RELEASED)==1);
        NSDate *retired=[NSDate dateWithTimeIntervalSinceNow:1];
        while(arc_present_query(out,ARC_PRESENT_LIVE_BYTES)!=0&&retired.timeIntervalSinceNow>0)pump();
        CHECK(arc_present_query(out,ARC_PRESENT_LIVE_BYTES)==0);
        CHECK(arc_present_destroy(out)==ARC_PRESENT_OK&&gl.subviews.count==children);
        glDeleteFramebuffersEXT(1,&sentinelFbo);glDeleteTextures(2,attachments);glDeleteTextures(1,&sentinel);
        free(source);arc_scene_destroy(scene);[window close];
        puts("Metal current-frame presentation: 3 real frames/resize, exact full pixels, current IDs, pacing/MRT state/deletion passed");
        return 0;
    }
}
