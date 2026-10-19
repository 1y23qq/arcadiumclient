// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.render.ItemInHandRenderer;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemInHandRenderer.class, priority = 2000)
public abstract class MixinNoRenderHand {
    @Inject(method = "renderInFirstPerson", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterHand(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.FIRST_PERSON_HAND)) ci.cancel();
    }

    @Inject(method = "renderOnFireEffect", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterFireOverlay(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.FIRE_OVERLAY)) ci.cancel();
    }

    @Inject(method = "renderInWaterEffect", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterWaterOverlay(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.WATER_OVERLAY)) ci.cancel();
    }

    @Inject(method = "renderInWallEffect", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterWallOverlay(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.IN_WALL_OVERLAY)) ci.cancel();
    }
}
