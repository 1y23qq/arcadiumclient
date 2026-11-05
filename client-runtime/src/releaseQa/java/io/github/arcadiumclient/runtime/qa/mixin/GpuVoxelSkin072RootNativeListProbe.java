// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072QualityControls;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GlStateManager.class,priority=2000)
public abstract class GpuVoxelSkin072RootNativeListProbe {
    @Inject(method="callList",at=@At("HEAD"))
    private static void voxel072$originalList(int displayList,CallbackInfo callback){GpuVoxelSkin072QualityControls.actualNativeRootList();}
}
