// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.PlayerFeatures073VoxelProjectionControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Minecraft.class,priority=1500)
public abstract class PlayerFeatures073VoxelProjectionStopProbe {
    @Inject(method="stop",at=@At("HEAD"))
    private void features073$projectionClose(CallbackInfo callback){PlayerFeatures073VoxelProjectionControl.finish();}
    @Inject(method="setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V",at=@At("HEAD"))
    private void features073$projectionWorld(ClientWorld world,String message,CallbackInfo callback){if(world==null)PlayerFeatures073VoxelProjectionControl.invalidate("world-unload");}
    @Inject(method="reloadResources()V",at=@At("HEAD"))
    private void features073$projectionReload(CallbackInfo callback){PlayerFeatures073VoxelProjectionControl.invalidate("resource-reload");}
}
