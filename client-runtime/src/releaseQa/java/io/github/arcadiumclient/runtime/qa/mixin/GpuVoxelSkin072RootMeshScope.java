// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072QualityControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets="org.polyfrost.skinlayers.render.CompiledMesh",remap=false)
public abstract class GpuVoxelSkin072RootMeshScope {
    @WrapMethod(method="render",remap=false)
    private void voxel072$mesh(float scale,Operation<Void> original){
        boolean scoped=GpuVoxelSkin072QualityControls.beginNativeRootMesh();
        try{original.call(scale);}finally{if(scoped)GpuVoxelSkin072QualityControls.endNativeRootMesh();}
    }
}
