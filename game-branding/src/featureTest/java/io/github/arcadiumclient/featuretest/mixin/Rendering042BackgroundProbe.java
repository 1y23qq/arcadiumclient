// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.Rendering042Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.polyfrost.polynametag.client.NametagRenderer", remap = false)
public abstract class Rendering042BackgroundProbe {
    @Inject(method = "backgroundQuads", at = @At("HEAD"), remap = false)
    private static void arcadium$background(float x, float y, float width, CallbackInfoReturnable<Integer> ci) {
        Rendering042Qa.background(x, y, width);
    }
}
