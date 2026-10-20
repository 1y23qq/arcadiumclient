// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.skins.LegacySkinImages;
import java.awt.image.BufferedImage;
import net.minecraft.client.render.texture.SkinImageProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkinImageProcessor.class)
public abstract class MixinLegacySkinImageProcessor {
    @Inject(method = "process", at = @At("HEAD"), cancellable = true)
    private void arcadium$modernSkin(BufferedImage source, CallbackInfoReturnable<BufferedImage> callback) {
        BufferedImage image = LegacySkinImages.normalize(source);
        if (image != null) callback.setReturnValue(image);
    }
}
