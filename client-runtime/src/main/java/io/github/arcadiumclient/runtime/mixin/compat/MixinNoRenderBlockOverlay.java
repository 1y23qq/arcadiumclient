// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat;

import io.github.arcadiumclient.runtime.client.features.NoRender;
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "tektonikal.customblockhighlight.Renderer", remap = false, priority = 2000)
public abstract class MixinNoRenderBlockOverlay {
    @Inject(method = "mainLoop", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcadium$filterBlockOverlay(CallbackInfo ci) {
        if (NoRender.hide(NoRenderPolicy.Effect.BLOCK_OUTLINE)) ci.cancel();
    }
}
