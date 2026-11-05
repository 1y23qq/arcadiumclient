// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinTextureBudgetTest {
    @Test void unsupportedDescriptorsAreRejectedBeforeReservingAnyNativeArrayStorage() {
        var budget=new GpuVoxelSkinTextureBudget();Object source=new Object();
        for(var descriptor:new GpuVoxelSkinTextureBudget.Descriptor[]{null,new GpuVoxelSkinTextureBudget.Descriptor(null,64,64,256),
                new GpuVoxelSkinTextureBudget.Descriptor("huge",256,256,256),new GpuVoxelSkinTextureBudget.Descriptor("layers",64,64,257)})
            assertFalse(budget.admit(source,descriptor));
        assertEquals(0,budget.bytes());assertEquals(0,budget.pools());assertEquals(0,budget.textures());
    }
    @Test void poolCountAndBytesAreBoundedAndKnownStorageIsReused() {
        var budget=new GpuVoxelSkinTextureBudget();Object source=new Object();
        for(int i=0;i<4;i++)assertTrue(budget.admit(source,new GpuVoxelSkinTextureBudget.Descriptor(i,128,128,128)));
        assertEquals(32L*1024*1024,budget.bytes());assertEquals(4,budget.pools());
        assertFalse(budget.admit(new Object(),new GpuVoxelSkinTextureBudget.Descriptor(4,64,64,2)));
        assertTrue(budget.admit(source,new GpuVoxelSkinTextureBudget.Descriptor(0,128,128,128)));
        assertFalse(budget.admit(source,new GpuVoxelSkinTextureBudget.Descriptor(0,128,64,256)));
        assertEquals(1,budget.textures());budget.reset();assertEquals(0,budget.bytes());assertEquals(0,budget.pools());
    }
    @Test void replacedTextureObjectsCannotGrowTheNativeIdentityCacheWithoutBound() {
        var budget=new GpuVoxelSkinTextureBudget();var descriptor=new GpuVoxelSkinTextureBudget.Descriptor("pool",64,64,256);Object first=new Object();
        assertTrue(budget.admit(first,descriptor));for(int i=1;i<1024;i++)assertTrue(budget.admit(new Object(),descriptor));
        assertFalse(budget.admit(new Object(),descriptor));assertTrue(budget.admit(first,descriptor));assertEquals(1024,budget.textures());
    }
    @Test void identifierAliasesOfOneTextureObjectHaveAnIndependentBound(){
        var budget=new GpuVoxelSkinTextureBudget();Object source=new Object();var descriptor=new GpuVoxelSkinTextureBudget.Descriptor("pool",64,64,256);
        for(int i=0;i<1024;i++)assertTrue(budget.admit(source,"alias/"+i,descriptor));
        assertFalse(budget.admit(source,"alias/1024",descriptor));assertTrue(budget.admit(source,new String("alias/0"),descriptor));
        assertEquals(1,budget.textures());assertEquals(1024,budget.identifiers());assertEquals(1,budget.pools());
        budget.reset();assertEquals(0,budget.identifiers());assertEquals(0,budget.textures());assertEquals(0,budget.bytes());
    }
}
