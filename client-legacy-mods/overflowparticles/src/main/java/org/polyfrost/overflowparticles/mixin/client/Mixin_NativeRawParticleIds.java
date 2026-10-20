package org.polyfrost.overflowparticles.mixin.client;

import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.*;
import org.polyfrost.overflowparticles.client.config.ParticleConfig;
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.utils.NativeParticleType;
import org.polyfrost.overflowparticles.utils.ParticleIdentifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class Mixin_NativeRawParticleIds {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void overflowparticles$identifyRaw(Particle particle, CallbackInfo ci) {
        NativeParticleType type = ParticleIdentifier.get(particle);
        if (type == null) type = NativeParticleType.current();
        if (type == null) {
            if (particle instanceof BlockDustParticle) type = NativeParticleType.BLOCK_DUST;
            else if (particle instanceof BlockParticle) type = NativeParticleType.BLOCK_CRACK;
            else if (particle instanceof ItemParticle) type = NativeParticleType.ITEM_CRACK;
            else if (particle instanceof RainSplashParticle) type = NativeParticleType.WATER_DROP;
            else if (particle instanceof EntityPickupParticle) type = NativeParticleType.ITEM_TAKE;
        }
        if (type != null) ParticleIdentifier.set(particle, type);
        ParticleConfig config = PerParticleConfigManager.getConfigByType(type);
        if (config != null && !config.getEnabled()) ci.cancel();
    }
}
