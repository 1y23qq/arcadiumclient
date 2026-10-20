package org.polyfrost.overflowparticles.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.world.World;
import org.polyfrost.overflowparticles.client.config.OverflowParticlesConfig;
import org.polyfrost.overflowparticles.utils.NativeParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class Mixin_CleanView {
    @WrapWithCondition(method = "tickStatusEffects", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/World;addParticle(Ljava/lang/String;DDDDDD)V"))
    private boolean overflowparticles$clean(World world, String name, double x, double y, double z,
                                           double dx, double dy, double dz) {
        Minecraft mc = Minecraft.getInstance();
        if (!OverflowParticlesConfig.isCleanView() || mc.options.perspective != 0 || (Object) this != mc.player) return true;
        NativeParticleType type = NativeParticleType.fromName(name);
        return type != NativeParticleType.SPELL_MOB && type != NativeParticleType.SPELL_MOB_AMBIENT;
    }
}
