package org.polyfrost.overflowparticles.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.Tesselator;
import org.polyfrost.overflowparticles.client.OverflowParticlesClient;
import org.polyfrost.overflowparticles.client.config.ParticleConfig;
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.client.particles.VanillaParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleManager.class)
public class Mixin_TrackRenderState {
    @WrapOperation(method = {"render", "renderLit"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/entity/particle/Particle;render(Lnet/minecraft/client/render/vertex/Tesselator;FFFFFF)V"))
    private void overflowparticles$render(Particle particle, Tesselator vertices, float tickDelta,
                                         float dx, float dy, float dz, float forwards, float sideways,
                                         Operation<Void> original) {
        ParticleConfig config = PerParticleConfigManager.getConfig(particle);
        if (config != null && !config.getEnabled()) return;
        Particle previousParticle = OverflowParticlesClient.getRenderingEntity();
        boolean previousRendering = OverflowParticlesClient.isRendering();
        OverflowParticlesClient.setRenderingEntity(particle);
        OverflowParticlesClient.setRendering(config != null && config.getParticleType() != VanillaParticles.BLOCKS.getId());
        try {
            original.call(particle, vertices, tickDelta, dx, dy, dz, forwards, sideways);
        } finally {
            OverflowParticlesClient.setRendering(previousRendering);
            OverflowParticlesClient.setRenderingEntity(previousParticle);
        }
    }
}
