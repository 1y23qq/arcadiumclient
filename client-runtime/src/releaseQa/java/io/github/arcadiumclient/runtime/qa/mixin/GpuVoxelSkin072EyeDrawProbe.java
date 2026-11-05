// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072EyePositionEvidence;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinDraw",remap=false)
public abstract class GpuVoxelSkin072EyeDrawProbe {
    @WrapOperation(method="draw",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL43;glMultiDrawArraysIndirect(IJII)V"),remap=false)
    private void voxel072$eyeDraw(int mode,long indirect,int count,int stride,Operation<Void> original,@Local(argsOnly=true)IntBuffer payload){
        var token=GpuVoxelSkin072EyePositionEvidence.beforeDraw(this,payload,count);
        try{original.call(mode,indirect,count,stride);GpuVoxelSkin072EyePositionEvidence.drew(token);}finally{GpuVoxelSkin072EyePositionEvidence.restore(token);}
    }
}
