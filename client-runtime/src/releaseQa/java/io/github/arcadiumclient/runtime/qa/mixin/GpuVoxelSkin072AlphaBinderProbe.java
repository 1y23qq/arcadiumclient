// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072AlphaSeed;
import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072TextureCopyEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="org.polyfrost.skinlayers.PlayerLayerBinder",remap=false)
public abstract class GpuVoxelSkin072AlphaBinderProbe {
    @Inject(method="bind",at=@At("TAIL"),remap=false)
    private static void voxel072$alphaPlayer(@Coerce Object model,@Coerce Object player,boolean slim,CallbackInfo callback){GpuVoxelSkin072AlphaSeed.observePlayer(player);GpuVoxelSkin072TextureCopyEvidence.observeModel(model,player);}
}
