// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import net.minecraft.client.render.entity.layer.WornSkullLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WornSkullLayer.class)
public abstract class HidePlayerHeadgearMixin {
    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void arcadium$hideHeadgear(LivingEntity entity, float walk, float amount, float delta,
                                      float age, float yaw, float pitch, float scale, CallbackInfo ci) {
        if (OneConfigConfig.hideHelmet && entity instanceof PlayerEntity) ci.cancel();
    }
}
