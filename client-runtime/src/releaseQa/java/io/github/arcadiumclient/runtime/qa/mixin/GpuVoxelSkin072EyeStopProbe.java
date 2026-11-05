// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072EyePositionEvidence;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Minecraft.class,priority=2000)
public abstract class GpuVoxelSkin072EyeStopProbe {
    @Inject(method="stop",at=@At("HEAD"))
    private void voxel072$eyeStop(CallbackInfo callback){GpuVoxelSkin072EyePositionEvidence.stop();}
}
