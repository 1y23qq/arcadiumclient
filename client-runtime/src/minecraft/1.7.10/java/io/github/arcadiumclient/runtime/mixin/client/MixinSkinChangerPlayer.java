// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.skins.SkinChanger;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinSkinChangerPlayer {
    @Inject(method = "getCapeTextureLocation", at = @At("HEAD"), cancellable = true)
    private void arcadium$selectedCape(CallbackInfoReturnable<Identifier> callback) {
        var uuid = ((ClientPlayerEntity) (Object) this).getUuid();
        if (SkinChanger.hasCapeOverride(uuid)) callback.setReturnValue(SkinChanger.capeTextureFor(uuid));
    }

    @Inject(method = "hasCape", at = @At("HEAD"), cancellable = true)
    private void arcadium$selectedCapeVisible(CallbackInfoReturnable<Boolean> callback) {
        var uuid = ((ClientPlayerEntity) (Object) this).getUuid();
        if (SkinChanger.hasCapeOverride(uuid)) callback.setReturnValue(SkinChanger.capeTextureFor(uuid) != null);
    }

    @Inject(method = "getSkinTextureLocation()Lnet/minecraft/resource/Identifier;", at = @At("HEAD"), cancellable = true)
    private void arcadium$uploadedSkin(CallbackInfoReturnable<Identifier> callback) {
        Identifier texture = SkinChanger.textureFor(((ClientPlayerEntity) (Object) this).getUuid());
        if (texture != null) callback.setReturnValue(texture);
    }
}
