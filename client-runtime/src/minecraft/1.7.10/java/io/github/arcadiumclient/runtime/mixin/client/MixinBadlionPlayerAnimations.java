// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.model.entity.HumanoidModel;
import io.github.arcadiumclient.runtime.client.features.AnimationsConfig;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class MixinBadlionPlayerAnimations {
    @Shadow private HumanoidModel player;
    @Inject(method = "render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V"))
    private void arcadium$inventoryPose(CallbackInfo ci) {
        var settings = AnimationsConfig.badlion();
        if (settings == null || !settings.inventory() || !BadlionAnimationContext.inInventory()) return;
        player.sneaking = false;
        player.aimingBow = false;
        if (player.itemInRightHand == 3) player.itemInRightHand = 1;
    }
}
