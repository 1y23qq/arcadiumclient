// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072EyePositionEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinRenderer",remap=false)
public abstract class GpuVoxelSkin072EyeTimeProbe {
    @ModifyVariable(method="submit",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private float voxel072$eyeIndex(float original){return GpuVoxelSkin072EyePositionEvidence.effectTime(original);}
}
