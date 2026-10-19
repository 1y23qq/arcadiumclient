// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.forge;

import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraftforge.client.GuiIngameForge", remap = false, priority = 2000)
public abstract class MixinNoRenderForgeOverlays {
    @Inject(method = "renderHelmet", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$filterForgeHelmet(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.PUMPKIN_OVERLAY)) ci.cancel();
    }

    @Inject(method = "renderPortal", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$filterForgePortal(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.PORTAL_OVERLAY)) ci.cancel();
    }
}
