package org.polyfrost.overflowparticles.mixin.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.ParticleManager;
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.client.particles.VanillaParticles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.10 {
/*@Mixin(ClientWorld.class)
*///?} else {
@Mixin(ParticleManager.class)
//?}
public class Mixin_CancelDiggingParticles {
    @Inject(
            // 26.3 merged the hit sound into addBreakingBlockEffects, so only the particle helper is cancelled
            //? if >=26.3 {
            /*method = "addBreakingParticles",
            *///?} elif >=1.21.10 {
            /*method = "addBreakingBlockEffect",
            *///?} elif >1.8.9 {
            /*method = "crack",
            *///?} else {
            method = "addBlockMiningParticles(IIII)V",
            //?}
            at = @At("HEAD"),
            cancellable = true
    )
    private void overflowparticles$cancelBlockHit(CallbackInfo ci) {
        if (!PerParticleConfigManager.getConfigByType(VanillaParticles.BLOCKS).getEnabled() || PerParticleConfigManager.getBlockSetting().getHideDigging()) {
            ci.cancel();
        }
    }
}
