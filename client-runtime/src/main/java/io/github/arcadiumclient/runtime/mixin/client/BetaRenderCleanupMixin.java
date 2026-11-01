// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.optimizations.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class BetaRenderCleanupMixin {
    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
    private void arcadium$releaseWorldBuffers(ClientWorld world, String reason, CallbackInfo callback) {
        if (world == null) { RetainedHudBuffers.closeAll(); WorldRenderScale.release(); }
    }
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void arcadium$releaseGpuBuffers(CallbackInfo callback) {
        RetainedHudBuffers.closeAll(); WorldRenderScale.release(); GpuModelTransforms.close();
    }
}
