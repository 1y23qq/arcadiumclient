// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.legacy.LegacyFullbrightConfig;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinLegacyFullbright {
    @Shadow private int[] lightMapPixels;
    @Inject(method = "updateLightMap", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/DynamicTexture;upload()V"))
    private void arcadium$renderBrightness(float delta, CallbackInfo ci) {
        if (!LegacyFullbrightConfig.getEnabled()) return;
        float strength = Math.max(0, Math.min(100, LegacyFullbrightConfig.getStrength())) / 100f;
        for (int i = 0; i < lightMapPixels.length; i++) {
            int original = lightMapPixels[i];
            int r = original >> 16 & 255, g = original >> 8 & 255, b = original & 255;
            r += Math.round((255 - r) * strength); g += Math.round((255 - g) * strength); b += Math.round((255 - b) * strength);
            lightMapPixels[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
    }
}
