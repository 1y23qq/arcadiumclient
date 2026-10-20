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
public class Mixin_CancelBreakingParticles {
    @Inject(
            //? if >=1.21.10 {
            /*method = "addDestroyBlockEffect",
            *///?} elif >1.8.9 {
            /*method = "destroy",
            *///?} else {
            method = "addBlockMiningParticles(IIILnet/minecraft/block/Block;I)V",
            //?}
            at = @At("HEAD"),
            cancellable = true
    )
    private void overflowparticles$cancelBlockDestroy(CallbackInfo ci) {
        if (!PerParticleConfigManager.getConfigByType(VanillaParticles.BLOCKS).getEnabled() || PerParticleConfigManager.getBlockSetting().getHideDigging()) {
            ci.cancel();
        }
    }
}
