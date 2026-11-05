// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072AlphaSeed;
import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072QualityControls;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.github.arcadiumclient.runtime.qa.Features062Qa", remap = false)
public abstract class GpuVoxelSkin072AlphaSeedMixin {
    @Inject(method = "seedSkin", at = @At("TAIL"), remap = false)
    private static void voxel072$alpha(Minecraft mc, CallbackInfo callback) throws Exception {
        GpuVoxelSkin072QualityControls.skinOffsets(); GpuVoxelSkin072AlphaSeed.seed(mc);
    }
}
