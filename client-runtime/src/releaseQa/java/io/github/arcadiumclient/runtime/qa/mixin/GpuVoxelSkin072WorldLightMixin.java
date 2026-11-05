// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072QualityControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa", remap = false)
public abstract class GpuVoxelSkin072WorldLightMixin {
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private static void voxel072$light(CallbackInfo callback) { GpuVoxelSkin072QualityControls.world(); }
}
