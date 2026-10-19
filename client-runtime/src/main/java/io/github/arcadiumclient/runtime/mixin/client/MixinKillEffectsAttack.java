// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import io.github.arcadiumclient.runtime.client.features.KillEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinKillEffectsAttack {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void arcadium$trackAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        KillEffects.attack(player, target);
    }
}
