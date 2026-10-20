package org.polyfrost.overflowparticles.mixin.client.particles;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.entity.particle.EmitterParticle;
//? if >1.8.9 {
/*import net.minecraft.core.particles.ParticleOptions;
*///?} else
import org.polyfrost.overflowparticles.utils.NativeParticleType;
import org.polyfrost.overflowparticles.client.config.ParticleConfig;
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.client.particles.ParticleInfo;
import org.polyfrost.overflowparticles.client.particles.ParticleRegistry;
import org.polyfrost.overflowparticles.client.utils.ParticleSpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EmitterParticle.class)
public class Mixin_ApplyMultiplierToEmitters {
    //? if >1.8.9 {
    /*@Shadow @Final private ParticleOptions particleType;
    *///?} else
    @Shadow private String type;

    @ModifyExpressionValue(method = "tick", at = @At(value = "CONSTANT", args = "intValue=16"))
    private int multiplier(int constant) {
        //? if >1.8.9 {
        /*ParticleInfo type = ParticleRegistry.of(this.particleType.getType());
        *///?} else
        ParticleInfo type = ParticleRegistry.of(NativeParticleType.fromName(this.type));
        if (type == null) {
            return constant;
        }

        ParticleConfig config = PerParticleConfigManager.getConfigByType(type);
        if (config.getMultiplier() == 1) {
            return constant;
        }

        return (int) (constant * config.getMultiplier());
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    //? if >=1.21.4 {
                    /*target = "Lnet/minecraft/client/multiplayer/ClientWorld;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
                    *///?} elif >1.8.9 {
                    /*target = "Lnet/minecraft/client/multiplayer/ClientWorld;addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"
                    *///?} else {
                    target = "Lnet/minecraft/world/World;addParticle(Ljava/lang/String;DDDDDD)V"
                    //?}
            )
    )
    private void cancel(CallbackInfo ci) {
        ParticleSpawner.setMultiplied(true);
    }
}
