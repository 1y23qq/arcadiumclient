// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client.branding;

import io.github.arcadiumclient.runtime.client.render.ArmorVisibility;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public abstract class HidePlayerArmorMixin {
    @Inject(method = "bindTexture(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;IF)I", at = @At("HEAD"), cancellable = true)
    private void arcadium$hideArmor(ClientPlayerEntity player, int pass, float delta, CallbackInfoReturnable<Integer> cir) {
        if (ArmorVisibility.hidden(4 - pass, OneConfigConfig.hideHelmet, OneConfigConfig.hideChestplate,
                OneConfigConfig.hideLeggings, OneConfigConfig.hideBoots)) cir.setReturnValue(-1);
    }
}
