package org.polyfrost.overflowparticles.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.polyfrost.overflowparticles.client.config.BlockParticleEntry;
import org.polyfrost.overflowparticles.client.config.ParticleConfig;
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.client.particles.VanillaParticles;
import org.polyfrost.overflowparticles.utils.NativeParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class Mixin_CancelFootstepsIfNeeded {
    @WrapWithCondition(method = "baseTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/World;addParticle(Ljava/lang/String;DDDDDD)V"))
    private boolean overflowparticles$running(World world, String name, double x, double y, double z,
                                             double dx, double dy, double dz) {
        if (!world.isClient || NativeParticleType.fromName(name) != NativeParticleType.BLOCK_CRACK) return true;
        ParticleConfig config = PerParticleConfigManager.getConfigByType(VanillaParticles.BLOCKS);
        if (!config.getEnabled()) return false;
        BlockParticleEntry entry = PerParticleConfigManager.getBlockSetting();
        return !entry.getHideRunning() || (entry.getHideMode() != 1 && ((Entity) (Object) this).isInvisible());
    }
}
