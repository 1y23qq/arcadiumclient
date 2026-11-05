// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.mixin.compat.argentum.MixinGpuModelBatch;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinAuxiliaryTest {
    @Test void auxiliaryOwnershipUsesIdentityAndIsBounded() {
        var owners=new GpuVoxelSkinAuxiliaries(2);Object main=new Object(),a=new String("same"),b=new String("same"),c=new Object();
        assertTrue(owners.register(main,a));assertTrue(owners.register(main,b));assertEquals(2,owners.size());
        assertFalse(owners.register(main,c));assertFalse(owners.register(new Object(),a));assertTrue(owners.register(main,a));
        owners.unregister(a);assertTrue(owners.register(main,c));assertFalse(owners.contains(a));assertTrue(owners.contains(b));
    }
    @Test void registrationSurvivesCleanupUntilTheFinallyUnregister() {
        var owners=new GpuVoxelSkinAuxiliaries(2);Object main=new Object(),auxiliary=new Object();
        assertTrue(owners.register(main,auxiliary));
        assertThrows(IllegalStateException.class,()->{try{assertTrue(owners.contains(auxiliary));throw new IllegalStateException("close failed");}
            finally{owners.unregister(auxiliary);}});
        assertFalse(owners.contains(auxiliary));assertEquals(0,owners.size());
    }
    @Test void actualAuxiliaryBeginHookKeepsExistingParentPlayerJobsAlive() throws Exception {
        boolean enabled=GpuVoxelSkinPipeline.enabled;GpuVoxelSkinPipeline.enabled=false;
        Hooks auxiliary=new Hooks(),main=new Hooks();
        var hook=MixinGpuModelBatch.class.getDeclaredMethod("arcadium$beginJobs",CallbackInfoReturnable.class);hook.setAccessible(true);
        try{
            GpuPlayerPackets.beginBatch();float[] template=new float[192];for(int part=0;part<12;part++)template[part*16+12]=template[part*16+13]=template[part*16+14]=1;
            int id=GpuPlayerPackets.registerTemplate(template);GpuPlayerPackets.add(new Matrix4f(),0,0,0,0,0,0,0,0,false,false,false,id);
            assertEquals(1,GpuPlayerPackets.count());assertTrue(GpuVoxelSkinPipeline.registerAuxiliary(main,auxiliary));
            hook.invoke(auxiliary,new CallbackInfoReturnable<Boolean>("beginBatch",false));
            assertEquals(1,GpuPlayerPackets.count());assertTrue(GpuVoxelSkinPipeline.isAuxiliaryBackend(auxiliary));
            hook.invoke(main,new CallbackInfoReturnable<Boolean>("beginBatch",false));assertEquals(0,GpuPlayerPackets.count());
        }finally{GpuVoxelSkinPipeline.unregisterAuxiliary(auxiliary);GpuVoxelSkinPipeline.enabled=enabled;GpuPlayerPackets.resetTemplates();}
    }
    private static final class Hooks extends MixinGpuModelBatch {}
}
