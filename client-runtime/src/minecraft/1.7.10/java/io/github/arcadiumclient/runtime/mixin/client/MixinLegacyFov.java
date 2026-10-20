// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.legacy.LegacyFovConfig;
import io.github.arcadiumclient.runtime.legacy.LegacyVisualMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GameRenderer.class)
public abstract class MixinLegacyFov {
    @ModifyExpressionValue(method = "tickFov", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/BaseLocalClientPlayerEntity;getAdjustedMovementSpeed()F"))
    private float arcadium$dynamicFov(float vanilla) {
        if (!ClientPreferences.getDynamicFov()) return 1f;
        if (LegacyFovConfig.getSpeed() == 100f && LegacyFovConfig.getSprint() == 100f
            && LegacyFovConfig.getFlying() == 100f && LegacyFovConfig.getAiming() == 100f) return vanilla;
        var player = Minecraft.getInstance().player;
        if (player == null) return vanilla;
        var used = player.getItemInUse();
        int bowTicks = player.hasItemInUse() && used != null && used.getItem() == Items.BOW
            ? player.getRemainingItemUseDuration() : -1;
        return LegacyVisualMath.movementFov(player.getAttribute(EntityAttributes.MOVEMENT_SPEED).get(),
            player.abilities.getWalkSpeed(), player.isSprinting(), player.abilities.flying, bowTicks,
            LegacyFovConfig.getSpeed(), LegacyFovConfig.getSprint(), LegacyFovConfig.getFlying(), LegacyFovConfig.getAiming());
    }
    @ModifyConstant(method = "getFov", constant = @Constant(floatValue = 60f))
    private float arcadium$submergedFov(float vanilla) {
        return LegacyFovConfig.getSubmerged() == 100f ? vanilla : LegacyVisualMath.submergedFov(vanilla, LegacyFovConfig.getSubmerged());
    }
}
