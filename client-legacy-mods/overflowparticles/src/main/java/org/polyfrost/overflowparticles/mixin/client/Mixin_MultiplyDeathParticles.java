package org.polyfrost.overflowparticles.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
//? if >1.8.9
//import net.minecraft.entity.EntityType;
import net.minecraft.entity.living.LivingEntity;
//? if >1.8.9 {
/*import net.minecraft.world.level.Level;
*///?} else {
import net.minecraft.world.World;
//?}
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.client.config.ParticleConfig;
import org.polyfrost.overflowparticles.client.particles.VanillaParticles;
import org.polyfrost.overflowparticles.client.utils.ParticleSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class Mixin_MultiplyDeathParticles extends Entity {
    //? if >1.8.9 {
    /*public Mixin_MultiplyDeathParticles(EntityType<?> arg, Level arg2) {
        super(arg, arg2);
    }

    @ModifyExpressionValue(method = "makePoofParticles", at = @At(value = "CONSTANT", args = "intValue=20"))
    *///?} else {
    public Mixin_MultiplyDeathParticles(World world) {
        super(world);
    }

    @ModifyExpressionValue(method = "tickPostDeath", at = @At(value = "CONSTANT", args = "intValue=20", ordinal = 1))
    //?}
    private int overflowparticles$applyMultiplier(int constant) {
        ParticleConfig config = PerParticleConfigManager.getConfigByType(VanillaParticles.EXPLOSION_NORMAL);
        if (config.getMultiplier() == 1) {
            return constant;
        }

        return (int) (constant * config.getMultiplier());
    }

    //? if >1.8.9 {
    /*@Inject(method = "makePoofParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    *///?} else
    @Inject(method = "tickPostDeath", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;addParticle(Ljava/lang/String;DDDDDD)V"))
    private void overflowparticles$updateMultiplyState(CallbackInfo ci) {
        ParticleSpawner.setMultiplied(true);
    }
}
