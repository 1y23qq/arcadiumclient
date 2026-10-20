package org.polyfrost.overflowparticles.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;
import org.polyfrost.overflowparticles.client.config.OverflowParticlesConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleManager.class)
public class Mixin_StaticParticleColor {
    @WrapOperation(method = {"render", "renderLit"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/entity/particle/Particle;getLightLevel(F)I"))
    private int overflowparticles$light(Particle particle, float tickDelta, Operation<Integer> original) {
        return OverflowParticlesConfig.isStaticParticleColor() ? 15728880 : original.call(particle, tickDelta);
    }
}
