// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.LevelHead045Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.arcadiumclient.runtime.client.bedwars.OverlayHttp", remap = false)
public abstract class LevelHead045HttpProbe {
    @Inject(method = "requestOnce", at = @At("HEAD"), cancellable = true)
    private void arcadium$preventLiveStats(CallbackInfoReturnable<Object> ci) {
        if (LevelHead045Qa.blockUnexpectedRequest()) ci.setReturnValue(null);
    }
}
