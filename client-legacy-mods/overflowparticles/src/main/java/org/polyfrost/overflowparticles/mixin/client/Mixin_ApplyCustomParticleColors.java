package org.polyfrost.overflowparticles.mixin.client;

import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.Tesselator;
import org.polyfrost.overflowparticles.client.OverflowParticlesClient;
import org.polyfrost.overflowparticles.client.config.ParticleConfig;
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.client.particles.VanillaParticles;
import org.polyfrost.overflowparticles.client.utils.ParticleSpawner;
import org.polyfrost.compose.render.PolyColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.ToIntFunction;

@Mixin(Tesselator.class)
public abstract class Mixin_ApplyCustomParticleColors {
    @Shadow public abstract void color(int red, int green, int blue, int alpha);

    @Inject(method = "color(FFFF)V", at = @At("HEAD"), cancellable = true)
    private void overflowparticles$color(float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (!OverflowParticlesClient.isRendering()) return;
        Particle particle = OverflowParticlesClient.getRenderingEntity();
        this.color(
            (int) (overflowparticles$adjust(particle, red, PolyColor::getRed) * 255f),
            (int) (overflowparticles$adjust(particle, green, PolyColor::getGreen) * 255f),
            (int) (overflowparticles$adjust(particle, blue, PolyColor::getBlue) * 255f),
            (int) (overflowparticles$adjust(particle, alpha, PolyColor::getAlpha) * 255f));
        ci.cancel();
    }

    @Unique
    private static float overflowparticles$adjust(Particle particle, float original, ToIntFunction<PolyColor> pick) {
        ParticleConfig config = PerParticleConfigManager.getConfig(particle);
        if (config == null || config.getParticleType() == VanillaParticles.FOOTSTEP.getId()) {
            return original;
        }

        PolyColor color = config.getColor();
        int newColor = pick.applyAsInt(color);
        // System.out.println(ParticleRegistry.location(config.getParticleType()) + " | Original: " + original + ", New: " + newColor);
        return ParticleSpawner.color(newColor, original, config);
    }
}
