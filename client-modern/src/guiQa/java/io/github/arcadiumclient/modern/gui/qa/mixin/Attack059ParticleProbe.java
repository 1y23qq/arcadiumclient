package io.github.arcadiumclient.modern.gui.qa.mixin;

import io.github.arcadiumclient.modern.gui.qa.Attack059Qa;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class Attack059ParticleProbe {
    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"))
    private void release$nativeEmitter(Entity entity, ParticleOptions particle, CallbackInfo ci) {
        if (particle == ParticleTypes.CRIT) Attack059Qa.criticalEmitters++;
    }
    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"))
    private void release$emitter(Entity entity, ParticleOptions particle, int life, CallbackInfo ci) {
        if (particle == ParticleTypes.CRIT) Attack059Qa.criticalEmitters++;
    }
}
