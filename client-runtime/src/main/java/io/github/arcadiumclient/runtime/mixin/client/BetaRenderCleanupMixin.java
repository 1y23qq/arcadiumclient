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
    @Inject(method = "reloadResources()V", at = @At("HEAD"))
    private void arcadium$invalidateParticleResources(CallbackInfo callback) {
        GpuParticles.tick();
        GpuTerrainCommands.reset();
        GpuPlayerPipeline.reset();
    }
    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
    private void arcadium$releaseWorldBuffers(ClientWorld world, String reason, CallbackInfo callback) {
        if (world == null) { RetainedHudBuffers.closeAll(); WorldRenderScale.release(); GpuNameTags.closeAll(); GpuParticles.close(); GpuTerrainCommands.reset(); GpuModelTransforms.clearFrameBudget(); GpuPlayerPipeline.reset(); }
    }
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void arcadium$releaseGpuBuffers(CallbackInfo callback) {
        RetainedHudBuffers.closeAll(); WorldRenderScale.release(); GpuModelTransforms.close(); GpuNameTags.closeAll(); GpuParticles.close();
        GpuTerrainCommands.reset();
    }
}
