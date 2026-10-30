// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat;

import io.github.arcadiumclient.modern.client.features.AttackParticleEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.overflowparticles.client.OverflowParticlesEventHandler", remap = false)
public class OverflowCriticalParticlesMixin {
    @Inject(method = "doCritical", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$exclusiveAttackParticles(Player player, Entity target, CallbackInfo ci) {
        if (AttackParticleEffects.suppressExtraCritical(player, target)) ci.cancel();
    }
}
