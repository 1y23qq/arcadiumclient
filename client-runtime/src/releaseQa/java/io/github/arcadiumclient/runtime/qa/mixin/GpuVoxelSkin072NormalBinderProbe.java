// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072NormalEvidence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.skinlayers.PlayerLayerBinder", remap = false)
public abstract class GpuVoxelSkin072NormalBinderProbe {
    @Inject(method = "bind", at = @At("TAIL"), remap = false)
    private static void voxel072$actor(@Coerce Object model, @Coerce Object player, boolean slim, CallbackInfo callback) {
        GpuVoxelSkin072NormalEvidence.observe(model, player);
    }
}
