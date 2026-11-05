// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072AlphaThresholdProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class GpuVoxelSkin072AlphaThresholdProbeMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void voxel072$alphaPrecision(CallbackInfo callback){GpuVoxelSkin072AlphaThresholdProbe.run();}
}
