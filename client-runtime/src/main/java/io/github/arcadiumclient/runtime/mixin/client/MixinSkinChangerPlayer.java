// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.resource.Identifier;
import io.github.arcadiumclient.runtime.client.skins.SkinChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinSkinChangerPlayer {
    @Inject(method = "getCapeTextureLocation", at = @At("HEAD"), cancellable = true)
    private void arcadium$selectedCape(CallbackInfoReturnable<Identifier> cir) {
        var uuid = ((ClientPlayerEntity) (Object) this).getUuid();
        if (SkinChanger.hasCapeOverride(uuid)) cir.setReturnValue(SkinChanger.capeTextureFor(uuid));
    }

    @Inject(method = "getSkinTextureLocation()Lnet/minecraft/resource/Identifier;", at = @At("HEAD"), cancellable = true)
    private void arcadium$uploadedSkin(CallbackInfoReturnable<Identifier> cir) {
        Identifier texture = SkinChanger.textureFor(((ClientPlayerEntity) (Object) this).getUuid());
        if (texture != null) cir.setReturnValue(texture);
    }

    @Inject(method = "getModelType", at = @At("HEAD"), cancellable = true)
    private void arcadium$uploadedModel(CallbackInfoReturnable<String> cir) {
        String model = SkinChanger.modelFor(((ClientPlayerEntity) (Object) this).getUuid());
        if (model != null) cir.setReturnValue(model);
    }
}
