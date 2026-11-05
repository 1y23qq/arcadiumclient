// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072AlphaSeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline", remap = false)
public abstract class GpuVoxelSkin072AlphaDrawProbe {
    @Inject(method = "drawNative", at = @At("HEAD"), remap = false)
    private static void voxel072$alphaState(Object geometry, Object commands, Object instances, CallbackInfo callback) { GpuVoxelSkin072AlphaSeed.gpuState(); }
}
