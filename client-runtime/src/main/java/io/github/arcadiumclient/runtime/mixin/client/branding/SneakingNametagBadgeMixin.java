// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.branding.PlayerBadgeRenderer;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class SneakingNametagBadgeMixin {
    @Unique private boolean arcadium$showSneakingBadge;

    @Inject(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V", at = @At("HEAD"))
    private void arcadium$selectBadge(LivingEntity entity, double x, double y, double z, CallbackInfo ci) {
        arcadium$showSneakingBadge = PlayerBadgeRenderer.nametagEligible(entity);
    }

    @WrapOperation(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;getWidth(Ljava/lang/String;)I"))
    private int arcadium$measure(TextRenderer font, String name, Operation<Integer> original) {
        return original.call(font, name) + (arcadium$showSneakingBadge ? PlayerBadgeRenderer.ADVANCE : 0);
    }

    @WrapOperation(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I"))
    private int arcadium$draw(TextRenderer font, String name, int x, int y, int color,
                              Operation<Integer> original) {
        if (arcadium$showSneakingBadge) {
            PlayerBadgeRenderer.draw(x, y, color);
            x += PlayerBadgeRenderer.ADVANCE;
        }
        return original.call(font, name, x, y, color);
    }
}
