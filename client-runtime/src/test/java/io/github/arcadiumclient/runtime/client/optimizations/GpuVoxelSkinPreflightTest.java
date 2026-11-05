// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinPreflightTest {
    @Test void QualifiedLeaseIsSingleUseAndRetainsNoReferencesAfterConsumption(){
        var lease=new GpuVoxelSkinPreflight();Object main=new Object(),parts=new Object(),fact=new Object();
        lease.arm(main,parts,0xFC0,"skin",192,384,7,fact);assertSame(fact,lease.fact());
        assertEquals(384,lease.consume(main,parts,0xFC0,new String("skin"),192,7));assertNull(lease.fact());
        assertEquals(-1,lease.consume(main,parts,0xFC0,"skin",192,7));
    }
    @Test void OtherBackendModelMaskTextureTemplateOrFrameCannotConsumeTheActorPreflight(){
        Object main=new Object(),parts=new Object(),fact=new Object();
        for(int mutation=0;mutation<6;mutation++){
            var lease=new GpuVoxelSkinPreflight();lease.arm(main,parts,0xFC0,"skin",192,384,7,fact);
            assertEquals(-1,lease.consume(mutation==0?new Object():main,mutation==1?new Object():parts,mutation==2?0x40:0xFC0,mutation==3?"other":"skin",mutation==4?0:192,mutation==5?8:7));
            assertNull(lease.fact());assertEquals(-1,lease.consume(main,parts,0xFC0,"skin",192,7));
        }
    }
    @Test void InvalidatedOrNewPreflightCannotLeakItsPreviousQualifiedActor(){
        var lease=new GpuVoxelSkinPreflight();Object main=new Object(),parts=new Object();
        lease.arm(main,parts,0xFC0,"skin",192,384,7,new Object());lease.clear();
        assertNull(lease.fact());assertEquals(-1,lease.consume(main,parts,0xFC0,"skin",192,7));
        lease.arm(main,parts,0xFC0,"skin",192,576,8,new Object());assertEquals(576,lease.consume(main,parts,0xFC0,"skin",192,8));
    }
    @Test void TwoActorJobsAndAllOuterInstancesMustFitBeforeAnyQueueIsChanged(){
        assertTrue(GpuVoxelSkinPreflight.hasRoom(8190,8192,65530,6));
        assertFalse(GpuVoxelSkinPreflight.hasRoom(8191,8192,65530,6));assertFalse(GpuVoxelSkinPreflight.hasRoom(8192,8192,0,6));
        assertFalse(GpuVoxelSkinPreflight.hasRoom(8190,8192,65531,6));assertFalse(GpuVoxelSkinPreflight.hasRoom(-1,8192,0,6));
        assertFalse(GpuVoxelSkinPreflight.hasRoom(0,1,0,6));assertFalse(GpuVoxelSkinPreflight.hasRoom(0,8192,0,13));
        assertTrue(GpuVoxelSkinPreflight.hasRoom(0,8192,65536,0));assertFalse(GpuVoxelSkinPreflight.hasRoom(0,8192,Integer.MAX_VALUE,6));
    }
    @Test void ProductionSelectionPrecedesActorMutationAndPreflightCannotSubmitActorOrVoxelJobs()throws Exception{
        var player=bytecode(GpuPlayerPipeline.class);MethodNode capture=player.methods.stream().filter(m->m.name.equals("capture")).findFirst().orElseThrow();
        int selection=-1,actor=-1,index=0;
        for(var instruction:capture.instructions){
            if(instruction instanceof MethodInsnNode call){
                if(call.owner.endsWith("/GpuVoxelSkinPipeline")&&call.name.equals("canUseAnalytic"))selection=index;
                if(call.owner.endsWith("/GpuPlayerPackets")&&call.name.equals("addActor"))actor=index;
            }index++;
        }
        assertTrue(selection>=0&&actor>selection,"Detailed skins must choose their callback route before the original P1 actor mutation");
        MethodNode preflight=bytecode(GpuVoxelSkinRenderer.class).methods.stream().filter(m->m.name.equals("preflight")).findFirst().orElseThrow();
        for(var instruction:preflight.instructions){
            if(instruction instanceof MethodInsnNode call&&call.owner.endsWith("/GpuPlayerPackets"))
                assertFalse(java.util.Set.of("add","addActor","addPose","addVariant","writePose").contains(call.name),"Preflight must not mutate actor/pose jobs");
            if(instruction instanceof FieldInsnNode field&&field.owner.endsWith("/GpuVoxelSkinNativeAccess"))
                assertNotEquals("submit",field.name,"Preflight must not submit a native instance");
        }
    }
    private static ClassNode bytecode(Class<?> type)throws Exception{
        try(var source=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){
            assertNotNull(source);ClassNode node=new ClassNode();new ClassReader(source).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return node;
        }
    }
}
