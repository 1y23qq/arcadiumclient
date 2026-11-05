// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072EyePositionEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline",remap=false)
public abstract class GpuVoxelSkin072EyeCaptureProbe {
    @Inject(method="beginBatch",at=@At("HEAD"),remap=false)
    private static void voxel072$eyeBatch(CallbackInfo callback){GpuVoxelSkin072EyePositionEvidence.beginBatch();}
    @WrapMethod(method="captureMesh",remap=false)
    private static boolean voxel072$eyeCapture(Object mesh,float scale,Operation<Boolean> original){
        var token=GpuVoxelSkin072EyePositionEvidence.beginCapture(mesh,scale);boolean accepted=false;
        try{accepted=original.call(mesh,scale);return accepted;}finally{GpuVoxelSkin072EyePositionEvidence.endCapture(token,accepted);}
    }
}
