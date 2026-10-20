// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.LegacyGlint040Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.glintcolorizer.legacy1710.NativeGlint", remap = false)
public abstract class LegacyGlint040Probe {
    @Inject(method = "gui(IIIIF)V", at = @At("HEAD"), remap = false)
    private static void arcadium$actualGuiCall(int x, int y, int width, int height, float z, CallbackInfo ci) {
        LegacyGlint040Qa.guiEntered(z);
    }
}
