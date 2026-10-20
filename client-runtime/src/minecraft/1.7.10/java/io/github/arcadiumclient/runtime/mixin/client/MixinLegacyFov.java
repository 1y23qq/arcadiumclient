// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.legacy.LegacyFovConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class MixinLegacyFov {
    @ModifyExpressionValue(method = "tickFov", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/BaseLocalClientPlayerEntity;getAdjustedMovementSpeed()F"))
    private float arcadium$dynamicFov(float vanilla) {
        return !ClientPreferences.getDynamicFov() || LegacyFovConfig.getEnabled() ? 1f : vanilla;
    }
    @ModifyExpressionValue(method = "getFov", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;fov:F"))
    private float arcadium$customFov(float vanilla) {
        if (!LegacyFovConfig.getEnabled()) return vanilla;
        var player = Minecraft.getInstance().player;
        int target = LegacyFovConfig.getNormal();
        if (player != null) {
            if (player.isSprinting()) target = LegacyFovConfig.getSprinting();
            if (player.abilities.flying) target = LegacyFovConfig.getFlying();
            var used = player.getItemInUse();
            if (used != null && player.getItemUseTimer() > 0 && "BOW".equals(used.getUseAction().name())) target = LegacyFovConfig.getBow();
        }
        return Math.max(30, Math.min(130, target));
    }
}
