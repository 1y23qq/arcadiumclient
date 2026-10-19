// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.model.entity.PlayerModel;
import io.github.arcadiumclient.runtime.client.features.AnimationsConfig;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationContext;
import io.github.arcadiumclient.runtime.client.features.BadlionAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class MixinBadlionPlayerAnimations {
    @Shadow public abstract PlayerModel getModel();

    @Inject(method = "setModelStatus", at = @At("TAIL"))
    private void arcadium$inventoryPose(ClientPlayerEntity player, CallbackInfo ci) {
        BadlionAnimationState settings = AnimationsConfig.badlion();
        if (settings == null || !settings.inventory() || !BadlionAnimationContext.inInventory()) return;
        PlayerModel model = getModel();
        model.sneaking = false;
        model.aimingBow = false;
        if (model.itemInRightHand == 3) model.itemInRightHand = 1;
    }
}
