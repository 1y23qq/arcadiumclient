// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.entity.living.LivingEntity;
import io.github.arcadiumclient.runtime.client.features.KillEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinKillEffectsDeath {
    @Inject(method = "doEvent", at = @At("TAIL"))
    private void arcadium$confirmDeath(byte event, CallbackInfo ci) {
        if (event == 3) KillEffects.death((LivingEntity) (Object) this);
    }
}
