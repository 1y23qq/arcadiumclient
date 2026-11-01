// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.Random;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelTransformsTest {
    @Test void oversizedSortFallsBackWithoutLeavingEncodedMatrices() {
        int size=4097;var data=new int[size*19];
        for(int i=0;i<size;i++) {
            var marker=GpuModelTransforms.enqueue(new Matrix4f().translation(i%83,i%13,i%47),0,0,0,.1f,.2f,.3f,new Matrix4f());
            int cursor=i*19;for(int c=0;c<4;c++)for(int r=0;r<3;r++)data[cursor++]=Float.floatToRawIntBits(marker.get(c,r));
            data[i*19+12]=i;
        }
        assertFalse(GpuModelTransforms.deferSort(data,size));
        assertFalse(GpuModelTransforms.hasJobs(IntBuffer.wrap(data)));
        for(int i=0;i<size;i++) {
            assertEquals(i%83,Float.intBitsToFloat(data[i*19+9]));
            assertEquals(i,data[i*19+12]);
        }
    }
    @Test void compactJobsPreserveTransformsAndFallbackPreservesInstanceMetadata() {
        Random random = new Random(2479);
        for (int i = 0; i < 2048; i++) {
            var root = new Matrix4f().translation(random.nextFloat()*80-40, random.nextFloat()*80-40, random.nextFloat()*80-40)
                    .rotateY(random.nextFloat()*6).scale(-0.9375f,-0.9375f,0.9375f);
            var box = new Matrix4f().translation(-0.25f,-0.125f,-0.125f).scale(i%2==0 ? 0.5f : -0.5f,0.75f,0.25f);
            float x=random.nextFloat(), y=random.nextFloat(), z=random.nextFloat();
            float rx=random.nextFloat()*6, ry=random.nextFloat()*6, rz=random.nextFloat()*6;
            var marker=GpuModelTransforms.enqueue(root,x,y,z,rx,ry,rz,box);
            var expected=new Matrix4f(root).translate(x,y,z).rotateZ(rz).rotateY(ry).rotateX(rx).mul(box);
            var actual=GpuModelTransforms.resolve((int)marker.m00(),new Matrix4f());
            assertTrue(expected.equals(actual,1e-6f),"Compact matrix mismatch");
            var instance=IntBuffer.allocate(19); int cursor=0;
            for(int col=0;col<4;col++)for(int row=0;row<3;row++)instance.put(cursor++,Float.floatToRawIntBits(marker.get(col,row)));
            for(int meta=12;meta<19;meta++)instance.put(meta,0x12340000+meta);
            GpuModelTransforms.recover(instance);
            for(int col=0;col<4;col++)for(int row=0;row<3;row++)assertEquals(expected.get(col,row),Float.intBitsToFloat(instance.get(col*3+row)),1e-6f);
            for(int meta=12;meta<19;meta++)assertEquals(0x12340000+meta,instance.get(meta));
        }
    }
}
