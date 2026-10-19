// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderParticle;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ParticleManager.class, priority = 2000)
public abstract class MixinNoRenderParticles {
    @Inject(method = "addParticle", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterNewParticle(CallbackInfoReturnable<Particle> cir, @Local(argsOnly = true) int type) {
        if (NoRender.policy().hidesParticle(type)) cir.setReturnValue(null);
    }

    @Inject(method = "addParticle", at = @At("RETURN"))
    private void arcadium$rememberParticleType(CallbackInfoReturnable<Particle> cir, @Local(argsOnly = true) int type) {
        Particle particle = cir.getReturnValue();
        if (particle != null) ((NoRenderParticle) particle).arcadium$particleType(type);
    }

    @Inject(method = "addBlockMiningParticles", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterBlockDebris(CallbackInfo ci) {
        if (NoRender.policy().hidesParticle(37)) ci.cancel();
    }

    @Inject(method = {"render", "renderLit"}, at = @At("HEAD"), cancellable = true)
    private void arcadium$filterAllParticles(CallbackInfo ci) {
        if ((NoRender.policy().particles & NoRenderPolicy.bit(NoRenderPolicy.ParticlePart.ALL)) != 0) ci.cancel();
    }

    @WrapWithCondition(method = {"render", "renderLit"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/particle/Particle;render(Lnet/minecraft/client/render/vertex/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private boolean arcadium$filterExistingParticle(Particle particle, BufferBuilder buffer, Entity camera, float tickDelta,
                                                   float x, float y, float z, float u, float v) {
        return !NoRender.hideParticle(particle);
    }
}
