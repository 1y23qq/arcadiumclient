// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa.mixin;

import io.github.arcadiumclient.modern.gui.qa.BackbufferQa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx", remap = false)
abstract class SkiaPresentQaMixin {
    @Inject(method = "draw", at = @At("RETURN"), remap = false)
    private void arcadium$capturePresentedFrame(CallbackInfo callback) { BackbufferQa.present(); }
}
