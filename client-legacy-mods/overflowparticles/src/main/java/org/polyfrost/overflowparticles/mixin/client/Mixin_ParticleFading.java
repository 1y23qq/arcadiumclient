package org.polyfrost.overflowparticles.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.Tesselator;
import org.polyfrost.overflowparticles.client.config.ParticleConfig;
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.client.particles.ParticleInfo;
import org.polyfrost.overflowparticles.client.particles.ParticleRegistry;
import org.polyfrost.overflowparticles.client.particles.VanillaParticles;
import org.polyfrost.overflowparticles.utils.NativeParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleManager.class)
public abstract class Mixin_ParticleFading {
    @WrapOperation(method = {"render", "renderLit"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/entity/particle/Particle;render(Lnet/minecraft/client/render/vertex/Tesselator;FFFFFF)V"))
    private void overflowparticles$fade(Particle particle, Tesselator vertices, float tickDelta,
                                       float dx, float dy, float dz, float forwards, float sideways,
                                       Operation<Void> original) {
        Mixin_AccessBillboardParticleData data = (Mixin_AccessBillboardParticleData) particle;
        float alpha = data.getAlpha();
        overflowparticles$applyFade(particle);
        try {
            original.call(particle, vertices, tickDelta, dx, dy, dz, forwards, sideways);
        } finally {
            data.overflowparticles$setAlpha(alpha);
        }
    }

    @Unique
    private static void overflowparticles$applyFade(Particle instance) {
        ParticleConfig config = PerParticleConfigManager.getConfig(instance);
        boolean fade = false;
        float fadeStart = 0.0f;

        if (config != null) {
            NativeParticleType typeId = config.getParticleType();
            ParticleInfo info = ParticleRegistry.of(typeId);

            fade = config.getFade();
            fadeStart = config.getFadeStart();

            if (info == VanillaParticles.BLOCKS) {
                fade = PerParticleConfigManager.getBlockSetting().getFade();
                fadeStart = PerParticleConfigManager.getBlockSetting().getFadeStart();
            }

            if (info != null && info.isFireworkTriggered()) {
                fade = false;
            }
        }

        if (fade) {
            if (!(fadeStart >= 0.0f) || fadeStart >= 1.0f || Float.isInfinite(fadeStart)) {
                fadeStart = 0.0f;
            }

            int lifetime = ((Mixin_AccessParticleData) instance).getLifetime();
            int age = ((Mixin_AccessParticleData) instance).getAge();
            if (lifetime > 0 && age >= 0) {
                float particleAge = (float) age / (float) lifetime;

                if (particleAge > fadeStart) {
                    float currentAlpha = ((Mixin_AccessBillboardParticleData) instance).getAlpha();

                    float newAlpha = overflowparticles$getNewAlpha(fadeStart, particleAge, currentAlpha);
                    if (!Float.isNaN(newAlpha) && newAlpha >= 0.0f && newAlpha != currentAlpha) {
                        ((Mixin_AccessBillboardParticleData) instance).overflowparticles$setAlpha(newAlpha);
                    }
                }
            }
        }
    }

    @Unique
    private static float overflowparticles$getNewAlpha(float fadeStart, float particleAge, float currentAlpha) {
        float denom = 1.0f - fadeStart;
        if (denom < 1e-6f) {
            denom = 1e-6f;
        }

        float t = (particleAge - fadeStart) / denom;
        if (t < 0.0f) t = 0.0f;
        if (t > 1.0f) t = 1.0f;

        return currentAlpha * (1.0f - t);
    }
}
