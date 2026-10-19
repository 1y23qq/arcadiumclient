// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.GameRenderer;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 2000)
public abstract class MixinNoRenderCamera {
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterWeather(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.WEATHER)) ci.cancel();
    }

    @Inject(method = "applyHurtCam", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterHurtCamera(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.HURT_CAMERA)) ci.cancel();
    }

    @Inject(method = "applyViewBobbing", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterViewBobbing(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.VIEW_BOBBING)) ci.cancel();
    }
}
