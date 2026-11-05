// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072EyePositionEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="org.embeddedt.embeddium.impl.render.shader.ShaderLoader",priority=500,remap=false)
public abstract class GpuVoxelSkin072EyeShaderProbe {
    @Inject(method="getShaderSource",at=@At("RETURN"),cancellable=true,remap=false)
    private static void voxel072$eyeShader(String name,CallbackInfoReturnable<String> callback){callback.setReturnValue(GpuVoxelSkin072EyePositionEvidence.shader(name,callback.getReturnValue()));}
}
