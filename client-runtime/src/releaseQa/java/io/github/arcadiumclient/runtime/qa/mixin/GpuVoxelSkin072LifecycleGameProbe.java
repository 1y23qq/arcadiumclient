// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.GpuVoxelSkin072LifecycleEvidence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class GpuVoxelSkin072LifecycleGameProbe {
    @Inject(method="shutdown",at=@At("HEAD"))
    private void voxel072$shutdown(CallbackInfo callback){GpuVoxelSkin072LifecycleEvidence.milestone("shutdown-head");}
    @Inject(method="reloadResources()V",at=@At("RETURN"))
    private void voxel072$resources(CallbackInfo callback){GpuVoxelSkin072LifecycleEvidence.milestone("resource-reload-return");}
    @Inject(method="setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V",at=@At("RETURN"))
    private void voxel072$world(ClientWorld world,String reason,CallbackInfo callback){GpuVoxelSkin072LifecycleEvidence.milestone(world==null?"world-unloaded":"world-replaced");}
}
