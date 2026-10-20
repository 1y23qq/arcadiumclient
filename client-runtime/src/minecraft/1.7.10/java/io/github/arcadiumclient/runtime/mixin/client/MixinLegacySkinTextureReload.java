// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.skins.LegacyPlayerSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public abstract class MixinLegacySkinTextureReload {
    @Inject(method = "reload", at = @At("HEAD"))
    private void arcadium$reloadSkinMeshes(ResourceManager resources, CallbackInfo callback) {
        Minecraft.getInstance().tell(LegacyPlayerSkins::clear);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void arcadium$discardSkinMesh(Identifier texture, CallbackInfo callback) {
        Minecraft.getInstance().tell(() -> LegacyPlayerSkins.discard(texture));
    }
}
