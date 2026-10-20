// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.block.entity.SkullRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerRenderer.class)
public abstract class HidePlayerHeadgearMixin {
    @WrapWithCondition(method = "renderMore(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/SkullRenderer;render(FFFIFILcom/mojang/authlib/GameProfile;)V"))
    private boolean arcadium$skull(SkullRenderer renderer, float x, float y, float z, int face, float yaw,
                                  int type, com.mojang.authlib.GameProfile owner) {
        return !OneConfigConfig.hideHelmet;
    }
    @WrapWithCondition(method = "renderMore(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;render(Lnet/minecraft/entity/living/LivingEntity;Lnet/minecraft/item/ItemStack;I)V", ordinal = 0))
    private boolean arcadium$headgear(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack, int pass) {
        return !OneConfigConfig.hideHelmet;
    }
}
