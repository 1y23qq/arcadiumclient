// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class NativePlayerPacketsTest {
    @AfterEach void clear() { GpuPlayerPackets.resetTemplates(); }

    @Test void nativeModelPivotsAndCustomRotationsKeepTheirExactComposition() {
        GpuPlayerPackets.resetTemplates();
        float[] template = template();
        int id = GpuPlayerPackets.registerTemplate(template);
        Random random = new Random(7201);
        for (int actor = 0; actor < 256; actor++) {
            Matrix4f root = new Matrix4f().translation(random.nextFloat()*60-30, random.nextFloat()*8, -random.nextFloat()*20)
                    .rotateY(random.nextFloat()*6).scale(-.9375f,-.9375f,.9375f);
            int job = GpuPlayerPackets.addPose(root, id);
            assertTrue(job >= 0);
            for (int part = 0; part < 12; part++) {
                float x=random.nextFloat(), y=random.nextFloat(), z=random.nextFloat();
                float rx=random.nextFloat()*6-3, ry=random.nextFloat()*6-3, rz=random.nextFloat()*6-3;
                GpuPlayerPackets.writePose(job,part,x,y,z,rx,ry,rz);
                Matrix4f expected = new Matrix4f(root).translate(x,y,z).rotateZ(rz).rotateY(ry).rotateX(rx)
                        .translate(-.25f,-.125f,.0625f).scale(.5f,.75f,.125f);
                assertTrue(expected.equals(GpuPlayerPackets.resolve(job*16+part,new Matrix4f()), .00001f));
            }
        }
    }

    @Test void twoDrawsOfAMutatedPartRetainSeparatePoseSnapshots() {
        GpuPlayerPackets.resetTemplates();
        int template = GpuPlayerPackets.registerTemplate(template());
        int first = GpuPlayerPackets.addPose(new Matrix4f(),template);
        GpuPlayerPackets.writePose(first,2,1,2,3,.1f,.2f,.3f);
        Matrix4f original = GpuPlayerPackets.resolve(first*16+2,new Matrix4f());
        int second = GpuPlayerPackets.addPose(new Matrix4f(),template);
        GpuPlayerPackets.writePose(second,2,7,8,9,1,2,3);
        assertTrue(original.equals(GpuPlayerPackets.resolve(first*16+2,new Matrix4f()),0));
        assertFalse(original.equals(GpuPlayerPackets.resolve(second*16+2,new Matrix4f()),.00001f));
        GpuPlayerPackets.rollback(second);
        assertThrows(IllegalArgumentException.class,()->GpuPlayerPackets.writePose(second,2,0,0,0,0,0,0));
    }

    @Test void sceneResetReclaimsTheBoundedTemplateBudget() {
        GpuPlayerPackets.resetTemplates();
        for (int i=0;i<256;i++) { float[] data=template();data[0]=i;GpuPlayerPackets.registerTemplate(data); }
        assertThrows(IllegalArgumentException.class,()->{float[] data=template();data[0]=256;GpuPlayerPackets.registerTemplate(data);});
        GpuPlayerPackets.resetTemplates();
        assertEquals(0,GpuPlayerPackets.registerTemplate(template()));
    }

    @Test void repeatedBackendProfilesReuseImmutableGeometryTemplates() {
        GpuPlayerPackets.resetTemplates();
        float[] first=template();
        assertEquals(0,GpuPlayerPackets.registerTemplate(first));
        for(int i=0;i<500;i++) assertEquals(0,GpuPlayerPackets.registerTemplate(template()));
        first[0]=123;
        assertEquals(0,GpuPlayerPackets.registerTemplate(template()));
        assertEquals(12*16,GpuPlayerPackets.registerTemplate(first));
    }

    @Test void geometryVariantsShareTheNativePoseWithoutChangingTheSourceGeometry() {
        GpuPlayerPackets.resetTemplates();
        int original=GpuPlayerPackets.registerTemplate(template());
        float[] neutral=new float[12*16];GpuPlayerPackets.copyTemplate(original,neutral);
        for(int i=0;i<12;i++) {int p=i*16;neutral[p+8]=neutral[p+9]=neutral[p+10]=0;neutral[p+12]=neutral[p+13]=neutral[p+14]=1;}
        int mesh=GpuPlayerPackets.registerTemplate(neutral);
        int source=GpuPlayerPackets.addPose(new Matrix4f().translate(1,2,3),original);
        GpuPlayerPackets.writePose(source,2,.25f,.5f,.75f,.1f,.2f,.3f);
        Matrix4f sourceBefore=GpuPlayerPackets.resolve(source*16+2,new Matrix4f());
        int variant=GpuPlayerPackets.addVariant(source,mesh);
        Matrix4f expected=new Matrix4f().translate(1,2,3).translate(.25f,.5f,.75f).rotateZ(.3f).rotateY(.2f).rotateX(.1f);
        assertTrue(expected.equals(GpuPlayerPackets.resolve(variant*16+2,new Matrix4f()),.00001f));
        assertTrue(sourceBefore.equals(GpuPlayerPackets.resolve(source*16+2,new Matrix4f()),0));
        GpuPlayerPackets.writePose(source,2,.5f,.75f,1,.1f,.2f,.3f);
        expected=new Matrix4f().translate(1,2,3).translate(.5f,.75f,1).rotateZ(.3f).rotateY(.2f).rotateX(.1f);
        assertTrue(expected.equals(GpuPlayerPackets.resolve(variant*16+2,new Matrix4f()),.00001f));
        assertThrows(IllegalArgumentException.class,()->GpuPlayerPackets.copyTemplate(1,new float[192]));
    }

    private static float[] template() {
        float[] data = new float[12*16];
        for (int part=0;part<12;part++) {
            int i=part*16;
            data[i+8]=-.25f;data[i+9]=-.125f;data[i+10]=.0625f;
            data[i+12]=.5f;data[i+13]=.75f;data[i+14]=.125f;
        }
        return data;
    }
}
