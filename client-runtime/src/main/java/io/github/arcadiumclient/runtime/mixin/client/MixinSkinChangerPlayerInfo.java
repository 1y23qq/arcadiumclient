// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.network.PlayerInfo;
import net.minecraft.resource.Identifier;
import io.github.arcadiumclient.runtime.client.skins.SkinChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class MixinSkinChangerPlayerInfo {
    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void arcadium$uploadedSkin(CallbackInfoReturnable<Identifier> cir) {
        Identifier texture = SkinChanger.textureFor(((PlayerInfo) (Object) this).getProfile().getId());
        if (texture != null) cir.setReturnValue(texture);
    }

    @Inject(method = "getModelType", at = @At("HEAD"), cancellable = true)
    private void arcadium$uploadedModel(CallbackInfoReturnable<String> cir) {
        String model = SkinChanger.modelFor(((PlayerInfo) (Object) this).getProfile().getId());
        if (model != null) cir.setReturnValue(model);
    }
}
