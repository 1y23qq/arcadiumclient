// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.gui.GameGui;
import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameGui.class, priority = 2000)
public abstract class MixinNoRenderOverlays {
    @Inject(method = "renderPumpkinOverlay", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterPumpkin(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.PUMPKIN_OVERLAY)) ci.cancel();
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterPortal(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.PORTAL_OVERLAY)) ci.cancel();
    }

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void arcadium$filterVignette(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.VIGNETTE)) ci.cancel();
    }
}
