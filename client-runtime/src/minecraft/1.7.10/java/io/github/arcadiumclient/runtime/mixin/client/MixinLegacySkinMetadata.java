// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.skins.LegacyPlayerSkins;
import net.minecraft.client.resource.skin.SkinManager;
import net.minecraft.client.resource.skin.SkinManager__SkinTextureCallback;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkinManager.class)
public abstract class MixinLegacySkinMetadata {
    @Inject(method = "register(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/resource/skin/SkinManager__SkinTextureCallback;Z)V", at = @At("HEAD"))
    private void arcadium$profileModel(GameProfile profile, SkinManager__SkinTextureCallback callback, boolean secure, CallbackInfo result) {
        LegacyPlayerSkins.profile(profile);
    }

    @Inject(method = "register(Lcom/mojang/authlib/minecraft/MinecraftProfileTexture;Lcom/mojang/authlib/minecraft/MinecraftProfileTexture$Type;Lnet/minecraft/client/resource/skin/SkinManager__SkinTextureCallback;)Lnet/minecraft/resource/Identifier;", at = @At("RETURN"))
    private void arcadium$skinModel(MinecraftProfileTexture texture, MinecraftProfileTexture.Type type,
                                    SkinManager__SkinTextureCallback callback, CallbackInfoReturnable<Identifier> result) {
        if (type == MinecraftProfileTexture.Type.SKIN) LegacyPlayerSkins.model(result.getReturnValue(), "slim".equals(texture.getMetadata("model")));
    }
}
