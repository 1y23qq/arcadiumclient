// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.GameRenderer;
import io.github.arcadiumclient.runtime.client.features.LightLevelOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinLightLevelOverlay {
    @Inject(method = "render(IFJ)V", at = @At(value = "CONSTANT", args = "stringValue=hand"))
    private void arcadium$groundLight(int eye, float tickDelta, long timeLimit, CallbackInfo ci) {
        LightLevelOverlay.render(tickDelta);
    }
}
