// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest.mixin;

import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.featuretest.LegacyWholeClientPerformance040Qa;
import net.minecraft.client.resource.skin.SkinManager;
import net.minecraft.client.resource.skin.SkinManager__SkinTextureCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkinManager.class)
public abstract class LegacyPerformance040SkinsMixin {
    @Inject(method = "register(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/resource/skin/SkinManager__SkinTextureCallback;Z)V", at = @At("HEAD"), cancellable = true)
    private void arcadium$syntheticSkins(GameProfile profile, SkinManager__SkinTextureCallback callback, boolean secure, CallbackInfo result) {
        if (LegacyWholeClientPerformance040Qa.suppressSyntheticSkinRequest(profile)) result.cancel();
    }
}
