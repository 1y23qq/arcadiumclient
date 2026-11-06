// SPDX-License-Identifier: GPL-3.0-only
#include "../arc_scene_passes.h"
#include "../arc_scene_lease.h"
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <dispatch/dispatch.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define CHECK(x) do { if(!(x)){fprintf(stderr,"Metal lease failure at%d: %s (%s)\n",__LINE__,#x,arc_scene_error(scene));return 1;} } while(0)

int main(int argc,const char **argv) {
    @autoreleasepool {
        void *scene=NULL;CHECK(argc==3);
        if(!MTLCreateSystemDefaultDevice()){puts("SKIP: No Metal device");return 77;}
        NSError *error=nil;NSString *source=[NSString stringWithContentsOfFile:[NSString stringWithUTF8String:argv[1]] encoding:NSUTF8StringEncoding error:&error];CHECK(source);
        scene=arc_scene_create(1,80,20,64);CHECK(scene&&arc_scene_metal_lease_abi()==1);
        void *device=NULL;CHECK(arc_scene_metal_lease_acquire(scene,ARC_METAL_DEVICE_LEASE,0,&device)==ARC_OK&&device);
        arc_scene_metal_info deviceInfo;CHECK(arc_scene_metal_lease_info(device,&deviceInfo)==ARC_OK&&deviceInfo.device&&deviceInfo.queue&&!deviceInfo.texture&&!deviceInfo.frame_id);
        void *duplicate=(void *)1;CHECK(arc_scene_metal_lease_acquire(scene,ARC_METAL_DEVICE_LEASE,0,&duplicate)==ARC_BUSY&&!duplicate);
        uint64_t compared=0;uint8_t pixels[48*32*4];
        for(int frame=1;frame<=2;frame++) {
            int width=frame==1?32:48,height=32;
            arc_scene_pipeline_state state={0,ARC_COMPARE_ALWAYS,0,0,1,0,1,0,0,0,15,0};
            CHECK(arc_scene_pipeline_state_put(scene,1,source.UTF8String,"vertexMain","fragmentMain",&state)==ARC_OK);
            float uniforms[16]={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
            arc_scene_pass pass={1,0,0,0,64,5,3,0,0,0,(uint32_t)width,(uint32_t)height,0,0,0,0,{.25f,.5f,.75f,1},1,0,1,0};
            CHECK(arc_scene_submit_passes(scene,width,height,uniforms,sizeof(uniforms),&pass,1,NULL,0)==ARC_OK);
            void *invalid=(void *)1;CHECK(arc_scene_metal_lease_acquire(scene,ARC_METAL_TARGET_LEASE,(uint64_t)frame+1,&invalid)==ARC_INVALID&&!invalid);
            void *target=NULL;CHECK(arc_scene_metal_lease_acquire(scene,ARC_METAL_TARGET_LEASE,(uint64_t)frame,&target)==ARC_OK&&target);
            arc_scene_metal_info info;CHECK(arc_scene_metal_lease_info(target,&info)==ARC_OK&&info.frame_id==(uint64_t)frame
                &&info.width==(uint32_t)width&&info.height==(uint32_t)height&&info.generation>0&&info.texture
                &&info.device==deviceInfo.device&&info.queue==deviceInfo.queue);
            duplicate=(void *)1;CHECK(arc_scene_metal_lease_acquire(scene,ARC_METAL_TARGET_LEASE,(uint64_t)frame,&duplicate)==ARC_BUSY&&!duplicate);
            uint64_t uploaded=arc_scene_query(scene,4);float value=0;
            CHECK(arc_scene_write(scene,0,&value,4)==ARC_BUSY&&arc_scene_query(scene,4)==uploaded&&arc_scene_wait(scene)==ARC_BUSY);
            __block int offThreadInfo=0,offThreadRelease=0;__block arc_scene_metal_info cleared;
            memset(&cleared,0x5a,sizeof(cleared));dispatch_semaphore_t done=dispatch_semaphore_create(0);
            NSThread *worker=[[NSThread alloc]initWithBlock:^{offThreadInfo=arc_scene_metal_lease_info(target,&cleared);offThreadRelease=arc_scene_metal_lease_release(target);dispatch_semaphore_signal(done);}];
            [worker start];CHECK(dispatch_semaphore_wait(done,dispatch_time(DISPATCH_TIME_NOW,INT64_C(5000000000)))==0);
            CHECK(offThreadInfo==ARC_INVALID&&offThreadRelease==ARC_INVALID&&arc_scene_query(scene,11)==1);
            for(size_t i=0;i<sizeof(cleared);i++)CHECK(((const uint8_t *)&cleared)[i]==0);
            id<MTLCommandQueue> queue=(__bridge id<MTLCommandQueue>)(void *)(uintptr_t)info.queue;
            id<MTLTexture> texture=(__bridge id<MTLTexture>)(void *)(uintptr_t)info.texture;
            id<MTLCommandBuffer> external=[queue commandBuffer];MTLRenderPassDescriptor *clear=[MTLRenderPassDescriptor new];
            clear.colorAttachments[0].texture=texture;clear.colorAttachments[0].loadAction=MTLLoadActionClear;clear.colorAttachments[0].storeAction=MTLStoreActionStore;
            clear.colorAttachments[0].clearColor=MTLClearColorMake(0,frame==1?1:0,frame==2?1:0,1);
            id<MTLRenderCommandEncoder> encoder=[external renderCommandEncoderWithDescriptor:clear];CHECK(external&&encoder);[encoder endEncoding];[external commit];
            CHECK(arc_scene_metal_lease_release(target)==ARC_OK&&arc_scene_wait(scene)==ARC_OK&&arc_scene_query(scene,11)==0);
            CHECK(arc_scene_read_rgba(scene,pixels,(uint64_t)width*height*4)==ARC_OK);
            for(int i=0;i<width*height;i++){CHECK(pixels[i*4]==0&&pixels[i*4+1]==(frame==1?255:0)&&pixels[i*4+2]==(frame==2?255:0)&&pixels[i*4+3]==255);compared+=4;}
            CHECK(arc_scene_reset(scene)==ARC_OK&&arc_scene_query(scene,10)==1);
        }
        CHECK(arc_scene_metal_lease_release(device)==ARC_OK&&arc_scene_wait(scene)==ARC_OK&&arc_scene_query(scene,10)==0&&arc_scene_query(scene,11)==0);
        arc_scene_destroy(scene);scene=NULL;FILE *report=fopen(argv[2],"w");CHECK(report);
        fprintf(report,"{\"passed\":true,\"frames\":2,\"externalQueueRgbaBytesExact\":%llu,\"ownerThreadGuards\":true,\"zeroedFailureOutputs\":true,\"frameMismatchGuards\":true,\"doubleLeaseRefused\":true,\"mutationsRefusedWhileBorrowed\":true,\"retirementAwaited\":true,\"deviceAndTargetLeaseCountsZero\":true,\"skikoQualified\":false,\"performanceClaim\":false}\n",(unsigned long long)compared);fclose(report);
        puts("Metal owner/frame leases, foreign queue writes, GPU retirement, resize/reset and zero live leases passed");return 0;
    }
}
