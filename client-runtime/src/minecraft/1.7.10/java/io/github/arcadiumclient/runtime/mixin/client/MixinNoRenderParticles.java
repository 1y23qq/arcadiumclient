// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.LegacyTesselatorBackend;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ParticleManager.class, priority = 2000)
public abstract class MixinNoRenderParticles {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void arcadium$newParticle(Particle particle, CallbackInfo ci) {
        if (NoRender.hideParticle(particle)) ci.cancel();
    }
    @Inject(method = {"handleBlockMining", "handleBlockBreaking"}, at = @At("HEAD"), cancellable = true)
    private void arcadium$blockDebris(CallbackInfo ci) {
        if (NoRender.policy().hidesParticle(37)) ci.cancel();
    }
    @Inject(method = {"render", "renderLit"}, at = @At("HEAD"), cancellable = true)
    private void arcadium$allParticles(CallbackInfo ci) {
        if ((NoRender.policy().particles & NoRenderPolicy.bit(NoRenderPolicy.ParticlePart.ALL)) != 0) ci.cancel();
    }
    @WrapWithCondition(method = {"render", "renderLit"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/particle/Particle;render(Lnet/minecraft/client/render/vertex/LegacyTesselatorBackend;FFFFFF)V"))
    private boolean arcadium$existingParticle(Particle particle, LegacyTesselatorBackend buffer, float delta,
                                              float x, float y, float z, float u, float v) {
        return !NoRender.hideParticle(particle);
    }
}
