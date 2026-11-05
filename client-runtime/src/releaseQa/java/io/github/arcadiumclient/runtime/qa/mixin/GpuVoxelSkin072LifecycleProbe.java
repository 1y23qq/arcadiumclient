// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072LifecycleEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline",remap=false)
public abstract class GpuVoxelSkin072LifecycleProbe {
    @Inject(method="reset",at=@At("HEAD"),remap=false)
    private static void voxel072$beforeReset(CallbackInfo callback){GpuVoxelSkin072LifecycleEvidence.beforeReset();}
    @Inject(method="reset",at=@At("RETURN"),remap=false)
    private static void voxel072$afterReset(CallbackInfo callback){GpuVoxelSkin072LifecycleEvidence.afterReset();}
}
