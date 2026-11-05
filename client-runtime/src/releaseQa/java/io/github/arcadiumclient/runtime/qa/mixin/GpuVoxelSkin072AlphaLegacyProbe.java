// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072AlphaSeed;
import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072TextureCopyEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="org.polyfrost.skinlayers.render.CompiledMesh",remap=false)
public abstract class GpuVoxelSkin072AlphaLegacyProbe {
    @Inject(method="render",at=@At("HEAD"),remap=false)
    private void voxel072$alphaMaterial(float scale,CallbackInfo callback){GpuVoxelSkin072AlphaSeed.legacyState(this,scale);GpuVoxelSkin072TextureCopyEvidence.legacy(this,scale);}
}
