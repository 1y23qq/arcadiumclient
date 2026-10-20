// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.legacy.LegacyFullbrightConfig;
import io.github.arcadiumclient.runtime.legacy.LegacyVisualMath;
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
        LegacyVisualMath.applyLightLevel(lightMapPixels, LegacyFullbrightConfig.getEnable(), LegacyFullbrightConfig.getLightLevel());
    }
}
