// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.management.ManagementFactory;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetainedTagTransactionsTest {
    @Test void failedLabelRestoresOnlyItsAppendedPacketsIncludingNewPasses(){
        boolean previous=RetainedNameTags.compact;RetainedNameTags.compact=true;
        GpuNameTags gpu=new GpuNameTags();
        try{
            var first=gpu.bindRetained(new BufferBuilder(128));var untouched=gpu.bindRetained(new BufferBuilder(128));
            var cache=new TagGeometryCache();int[] quad={0,0,0,-1,Float.floatToIntBits(1),0,0,-1,Float.floatToIntBits(1),Float.floatToIntBits(1),0,-1,0,Float.floatToIntBits(1),0,-1};
            var mesh=cache.mesh(quad,quad.length,4,3,false,false);var matrix=new Matrix4f().translate(2,3,4);
            gpu.queueBoundRetained(first,mesh,255,matrix,80,160);gpu.queueBoundRetained(untouched,mesh,127,matrix,240,240);
            gpu.checkpointRetained();
            gpu.queueBoundRetained(first,mesh,31,matrix,1,2);
            var created=gpu.bindRetained(new BufferBuilder(128));gpu.queueBoundRetained(created,mesh,64,matrix,3,4);
            gpu.rollbackRetained();
            assertEquals(1,first.count);assertEquals(1,untouched.count);assertEquals(0,created.count);
            assertNull(first.meshes[1]);assertNull(created.meshes[0]);assertEquals(255,first.instances.get(12));
            gpu.checkpointRetained();gpu.queueBoundRetained(first,mesh,22,matrix,5,6);gpu.commitRetained();
            gpu.checkpointRetained();gpu.queueBoundRetained(created,mesh,33,matrix,7,8);gpu.rollbackRetained();
            assertEquals(2,first.count);assertEquals(1,untouched.count);assertEquals(0,created.count);
            gpu.discard();assertEquals(0,first.count);assertEquals(0,untouched.count);
        }finally{gpu.close();RetainedNameTags.compact=previous;}
    }
    @Test void steadyOptionChecksAllocateNoPrimitiveWrappers()throws Throwable {
        var options=new RetainedTagOptions(RetainedTagPreparationTest.Options.class);
        for(int i=0;i<50000;i++)options.poll();
        var allocations=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().threadId();for(int i=0;i<8;i++)allocations.getThreadAllocatedBytes(thread);
        long before=allocations.getThreadAllocatedBytes(thread);
        for(int i=0;i<100000;i++)options.poll();
        long allocated=allocations.getThreadAllocatedBytes(thread)-before;
        assertTrue(allocated<1024,"Primitive option polling allocated repeatedly: "+allocated);
    }
    public static void main(String[] args)throws Throwable {
        var test=new RetainedTagTransactionsTest();test.failedLabelRestoresOnlyItsAppendedPacketsIncludingNewPasses();
        test.steadyOptionChecksAllocateNoPrimitiveWrappers();
        System.out.println("Retained tag transactions: rollback, untouched passes, cleanup and bounded option polling allocation passed");
    }
}
