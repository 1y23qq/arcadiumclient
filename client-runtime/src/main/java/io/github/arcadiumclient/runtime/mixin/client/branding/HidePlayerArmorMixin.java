// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import io.github.arcadiumclient.runtime.client.render.ArmorVisibility;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArmorLayer.class)
public abstract class HidePlayerArmorMixin {
    @Inject(method = "renderArmor(Lnet/minecraft/entity/living/LivingEntity;FFFFFFFI)V", at = @At("HEAD"), cancellable = true)
    private void arcadium$hideArmor(LivingEntity entity, float walk, float amount, float delta,
                                    float age, float yaw, float pitch, float scale, int slot, CallbackInfo ci) {
        if (entity instanceof PlayerEntity && ArmorVisibility.hidden(slot,
                OneConfigConfig.hideHelmet, OneConfigConfig.hideChestplate,
                OneConfigConfig.hideLeggings, OneConfigConfig.hideBoots)) ci.cancel();
    }
}
