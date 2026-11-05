// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;
import java.util.BitSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinImagePolicyTest {
    @Test void transparentPaddingOutsideTheUsedDomainDoesNotRejectAnOpaqueLayer(){
        byte[] alpha=new byte[4096];BitSet used=new BitSet(4096);used.set(70,74);
        for(int p=used.nextSetBit(0);p>=0;p=used.nextSetBit(p+1))alpha[p]=(byte)255;
        assertEquals(GpuVoxelSkinImagePolicy.Alpha.OPAQUE,GpuVoxelSkinImagePolicy.classify(alpha,used));
        alpha[71]=0;assertEquals(GpuVoxelSkinImagePolicy.Alpha.BINARY,GpuVoxelSkinImagePolicy.classify(alpha,used));
        for(int value:new int[]{1,64,128,192,254}){alpha[72]=(byte)value;assertEquals(GpuVoxelSkinImagePolicy.Alpha.FRACTIONAL,GpuVoxelSkinImagePolicy.classify(alpha,used));}
    }
    @Test void FaceTexelsIncludeTransparentCellsThatTheMesherOmitted(){
        byte[] alpha=new byte[4096];Arrays.fill(alpha,(byte)255);alpha[130]=0;
        BitSet face=new BitSet(4096);face.set(130);BitSet domain=GpuVoxelSkinImagePolicy.footprint(new float[0],face,9728,9728,10497,10497);
        assertEquals(GpuVoxelSkinImagePolicy.Alpha.BINARY,GpuVoxelSkinImagePolicy.classify(alpha,domain));
    }
    @Test void QuadFootprintIncludesBoundarySamplesAndRespectsBothWrapModes(){
        float[] quad=quad(-1f/64,0,0,1f/64);BitSet empty=new BitSet(4096);
        BitSet repeated=GpuVoxelSkinImagePolicy.footprint(quad,empty,9728,9728,10497,10497);
        assertTrue(repeated.get(63));assertTrue(repeated.get(0));assertTrue(repeated.get(127));assertTrue(repeated.get(64));
        BitSet clamped=GpuVoxelSkinImagePolicy.footprint(quad,empty,9728,9728,33071,33071);
        assertFalse(clamped.get(63));assertTrue(clamped.get(0));assertTrue(clamped.get(64));
        byte[] alpha=new byte[4096];Arrays.fill(alpha,(byte)255);alpha[63]=64;
        assertEquals(GpuVoxelSkinImagePolicy.Alpha.FRACTIONAL,GpuVoxelSkinImagePolicy.classify(alpha,repeated));
        assertEquals(GpuVoxelSkinImagePolicy.Alpha.OPAQUE,GpuVoxelSkinImagePolicy.classify(alpha,clamped));
    }
    @Test void UnknownGeometryImagesAndUnqualifiedSamplersCannotEnterTheBatch(){
        BitSet domain=new BitSet(4096);domain.set(0);
        assertEquals(GpuVoxelSkinImagePolicy.Alpha.UNKNOWN,GpuVoxelSkinImagePolicy.classify(new byte[4095],domain));
        assertEquals(GpuVoxelSkinImagePolicy.Alpha.UNKNOWN,GpuVoxelSkinImagePolicy.classify(new byte[4096],new BitSet()));
        domain.set(4096);assertEquals(GpuVoxelSkinImagePolicy.Alpha.UNKNOWN,GpuVoxelSkinImagePolicy.classify(new byte[4096],domain));
        for(int filter:new int[]{9729,9984,9985,9986,9987})assertNull(GpuVoxelSkinImagePolicy.footprint(quad(0,0,1,1),domain,filter,filter,10497,10497));
        assertNull(GpuVoxelSkinImagePolicy.footprint(quad(0,0,1,1),domain,9728,9728,33648,10497));
        assertNull(GpuVoxelSkinImagePolicy.footprint(new float[22],domain,9728,9728,10497,10497));
        assertNull(GpuVoxelSkinImagePolicy.footprint(quad(Float.NaN,0,1,1),domain,9728,9728,10497,10497));
    }
    @Test void CacheStampInvalidatesOnUploadImageReplacementGlNameAndSourceIdentity(){
        Object source=new Object(),image=new Object();var stamp=new GpuVoxelSkinImagePolicy.Stamp(source,image,42,17);
        assertTrue(stamp.matches(source,image,42,17));assertFalse(stamp.matches(new Object(),image,42,17));
        assertFalse(stamp.matches(source,new Object(),42,17));assertFalse(stamp.matches(source,image,43,17));assertFalse(stamp.matches(source,image,42,18));
    }
    @Test void OnlyTheQualifiedThirdPersonDimensionsAreAdmitted(){
        assertTrue(GpuVoxelSkinImagePolicy.defaultDimensions(new GpuVoxelSkinMath.Options(1.15f,1.05f,1.18f,2,3)));
        assertFalse(GpuVoxelSkinImagePolicy.defaultDimensions(new GpuVoxelSkinMath.Options(Math.nextUp(1.15f),1.05f,1.18f,1.1f,1.1f)));
        assertFalse(GpuVoxelSkinImagePolicy.defaultDimensions(new GpuVoxelSkinMath.Options(1.15f,1.08f,1.18f,1.1f,1.1f)));
        assertFalse(GpuVoxelSkinImagePolicy.defaultDimensions(new GpuVoxelSkinMath.Options(1.15f,1.05f,1.32f,1.1f,1.1f)));
    }
    @Test void SizedAndLegacyUnsizedRgbaMustActuallyHaveEightBitsPerChannel(){
        assertTrue(GpuVoxelSkinImagePolicy.rgba8(32856,8,8,8,8));assertTrue(GpuVoxelSkinImagePolicy.rgba8(6408,8,8,8,8));
        assertFalse(GpuVoxelSkinImagePolicy.rgba8(32856,8,8,8,0));assertFalse(GpuVoxelSkinImagePolicy.rgba8(6408,16,16,16,16));
        assertFalse(GpuVoxelSkinImagePolicy.rgba8(34842,16,16,16,16));
    }
    @Test void AnalyticAndCapturedPreflightMustBothRejectHeadReadyBodyPending(){
        int head=1<<6,body=1<<11,visible=head|body;
        int analyticRequired=GpuVoxelSkinImagePolicy.requiredParts(head|body,visible);
        int capturedRequired=GpuVoxelSkinImagePolicy.requiredParts(head|body,visible);
        assertFalse(GpuVoxelSkinImagePolicy.attachmentsReady(analyticRequired,head,head));
        assertFalse(GpuVoxelSkinImagePolicy.attachmentsReady(capturedRequired,head,head));
        assertFalse(GpuVoxelSkinImagePolicy.attachmentsReady(capturedRequired,head|body,head));
        assertTrue(GpuVoxelSkinImagePolicy.attachmentsReady(capturedRequired,head|body,head|body));
        assertTrue(GpuVoxelSkinImagePolicy.attachmentsReady(GpuVoxelSkinImagePolicy.requiredParts(head,visible),head,head));
        assertTrue(GpuVoxelSkinImagePolicy.attachmentsReady(GpuVoxelSkinImagePolicy.requiredParts(head|body,head),head,head));
    }
    @Test void ANewOpaqueCpuImageCannotReplaceFractionalPixelsWithoutNativeInvalidation(){
        Object source=new Object(),oldImage=new Object(),newImage=new Object();
        var previous=new GpuVoxelSkinImagePolicy.Stamp(source,oldImage,7,9);
        byte[] oldAlpha=new byte[4096],newAlpha=new byte[4096];Arrays.fill(oldAlpha,(byte)128);Arrays.fill(newAlpha,(byte)255);
        BitSet domain=new BitSet(4096);domain.set(0,4096);
        assertEquals(GpuVoxelSkinImagePolicy.Alpha.FRACTIONAL,GpuVoxelSkinImagePolicy.classify(oldAlpha,domain));
        assertEquals(GpuVoxelSkinImagePolicy.Alpha.OPAQUE,GpuVoxelSkinImagePolicy.classify(newAlpha,domain));
        assertTrue(GpuVoxelSkinImagePolicy.ambiguousImage(previous,false,source,newImage,7,9));
        assertTrue(GpuVoxelSkinImagePolicy.ambiguousImage(previous,true,source,oldImage,7,9));
        assertFalse(GpuVoxelSkinImagePolicy.ambiguousImage(previous,true,source,newImage,8,9));
        assertFalse(GpuVoxelSkinImagePolicy.ambiguousImage(previous,true,source,newImage,7,10));
        assertFalse(GpuVoxelSkinImagePolicy.ambiguousImage(previous,true,new Object(),newImage,7,9));
    }
    private static float[] quad(float u0,float v0,float u1,float v1){
        float[] q=new float[23];q[2]=1;for(int vertex=0;vertex<4;vertex++){q[6+vertex*5]=vertex==0||vertex==3?u0:u1;q[7+vertex*5]=vertex<2?v0:v1;}return q;
    }
}
