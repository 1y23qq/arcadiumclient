// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class NoRenderParticlesMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void arcadium$particle(ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) { if (NoRender.particle(particle)) cir.setReturnValue(null); }
    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void arcadium$all(CallbackInfo ci) {
        if ((NoRender.policy().particles & NoRenderPolicy.bit(NoRenderPolicy.ParticlePart.ALL)) != 0) ci.cancel();
    }
}
