// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072NormalEvidence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.skinlayers.render.CompiledMesh", remap = false)
public abstract class GpuVoxelSkin072NormalProbe {
    @Shadow @Final private float[] quads;
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void voxel072$normalState(float scale, CallbackInfo callback) { GpuVoxelSkin072NormalEvidence.sample(this, scale, quads); }
}
