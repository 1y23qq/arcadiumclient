// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativePlayerPacketsTest {
    @AfterEach void clear() { GpuPlayerPackets.resetTemplates(); GpuResidentPlayerState.enabled=false; }

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

    @Test void explicitPoseRowsRemainDenseAcrossAnalyticJobsAndSharedVariants() {
        GpuPlayerPackets.resetTemplates();
        int template=GpuPlayerPackets.registerTemplate(template());
        for(int i=0;i<20;i++)assertEquals(i,GpuPlayerPackets.add(new Matrix4f(),0,0,0,0,0,0,0,0,false,false,false,template));
        int first=GpuPlayerPackets.addPose(new Matrix4f().translate(1,2,3),template);
        GpuPlayerPackets.writePose(first,0,4,5,6,.1f,.2f,.3f);
        Matrix4f expected=GpuPlayerPackets.resolve(first*16,new Matrix4f());
        int variant=GpuPlayerPackets.addVariant(first,template);
        for(int i=0;i<9;i++)GpuPlayerPackets.add(new Matrix4f(),0,0,0,0,0,0,0,0,false,false,false,template);
        int second=GpuPlayerPackets.addPose(new Matrix4f().translate(7,8,9),template);
        GpuPlayerPackets.writePose(second,0,1,2,3,.4f,.5f,.6f);
        assertEquals(2,GpuPlayerPackets.explicitPoseRows());
        assertTrue(expected.equals(GpuPlayerPackets.resolve(first*16,new Matrix4f()),0));
        assertTrue(expected.equals(GpuPlayerPackets.resolve(variant*16,new Matrix4f()),0));
        GpuPlayerPackets.rollback(second);assertEquals(1,GpuPlayerPackets.explicitPoseRows());
        for(int i=GpuPlayerPackets.count()-1;i>variant;i--)GpuPlayerPackets.rollback(i);
        GpuPlayerPackets.rollback(variant);assertEquals(1,GpuPlayerPackets.explicitPoseRows());
        assertTrue(expected.equals(GpuPlayerPackets.resolve(first*16,new Matrix4f()),0));
        GpuPlayerPackets.rollback(first);assertEquals(0,GpuPlayerPackets.explicitPoseRows());
        int replacement=GpuPlayerPackets.addPose(new Matrix4f(),template);
        GpuPlayerPackets.writePose(replacement,0,8,9,10,0,0,0);assertEquals(1,GpuPlayerPackets.explicitPoseRows());
    }

    @Test void residentBodiesKeepDenseSlotsDespiteInterleavedArmorAndRenderOrder() {
        GpuPlayerPackets.resetTemplates();GpuResidentPlayerState.enabled=true;
        Object world=new Object(),a=new Object(),b=new Object(),c=new Object();
        int template=GpuPlayerPackets.registerTemplate(template());
        GpuPlayerPackets.prepareActors(world,List.of(a,b,c),true);long epoch=GpuPlayerPackets.actorEpoch();GpuPlayerPackets.beginBatch();
        assertEquals(3,GpuPlayerPackets.count());assertEquals(3,GpuPlayerPackets.residentActorCount());
        assertEquals(2,actor(c,3,template));int armor=GpuPlayerPackets.addPose(new Matrix4f(),template);assertEquals(3,armor);
        GpuPlayerPackets.writePose(armor,0,1,2,3,0,0,0);assertEquals(1,GpuPlayerPackets.explicitPoseRows());
        assertEquals(0,actor(a,1,template));assertEquals(1,actor(b,2,template));
        Matrix4f first=GpuPlayerPackets.resolve(0,new Matrix4f());int duplicate=actor(a,9,template);
        assertEquals(4,duplicate);assertTrue(first.equals(GpuPlayerPackets.resolve(0,new Matrix4f()),0));
        assertFalse(first.equals(GpuPlayerPackets.resolve(duplicate*16,new Matrix4f()),0));
        GpuPlayerPackets.prepareActors(world,List.of(c,a,b),true);GpuPlayerPackets.beginBatch();
        assertEquals(epoch,GpuPlayerPackets.actorEpoch());assertEquals(0,actor(a,5,template));assertEquals(2,actor(c,7,template));
        Matrix4f retained=GpuPlayerPackets.resolve(2*16,new Matrix4f());
        GpuPlayerPackets.prepareActors(world,List.of(c,a),true);GpuPlayerPackets.beginBatch();
        assertEquals(2,GpuPlayerPackets.count());assertTrue(retained.equals(GpuPlayerPackets.resolve(16,new Matrix4f()),0));
        assertEquals(1,actor(c,11,template));assertEquals(0,actor(a,13,template));
        GpuPlayerPackets.prepareActors(new Object(),List.of(a),true);GpuPlayerPackets.beginBatch();
        assertNotEquals(epoch,GpuPlayerPackets.actorEpoch());assertEquals(0,actor(a,17,template));
        GpuPlayerPackets.prepareActors(world,List.of(a),false);GpuPlayerPackets.beginBatch();
        assertEquals(0,GpuPlayerPackets.residentActorCount());assertEquals(0,GpuPlayerPackets.count());assertEquals(0,actor(a,23,template));
    }

    private static int actor(Object owner,float x,int template) {
        return GpuPlayerPackets.addActor(owner,new Matrix4f().translation(x,0,0),0,0,0,0,0,0,0,0,false,false,false,template);
    }

    @Test void nonPowerOfTwoActorReservationsCannotOvergrowTheCpuBudget() {
        int first=3000*GpuPlayerPackets.PLAYER_WORDS,maximum=GpuPlayerPackets.MAX_PLAYERS*GpuPlayerPackets.PLAYER_WORDS;
        int intermediate=GpuPlayerPackets.grownPlayerWords(first,first+GpuPlayerPackets.PLAYER_WORDS);
        assertEquals(first*2,intermediate);assertEquals(maximum,GpuPlayerPackets.grownPlayerWords(intermediate,maximum));
        assertEquals(maximum,GpuPlayerPackets.grownPlayerWords(maximum,1));
        assertThrows(IllegalArgumentException.class,()->GpuPlayerPackets.grownPlayerWords(maximum,maximum+1));
        assertThrows(IllegalArgumentException.class,()->GpuPlayerPackets.grownPlayerWords(-1,1));
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
