// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072InterpolationProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class GpuVoxelSkin072InterpolationProbeMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void voxel072$interpolation(CallbackInfo callback){GpuVoxelSkin072InterpolationProbe.run();}
}
