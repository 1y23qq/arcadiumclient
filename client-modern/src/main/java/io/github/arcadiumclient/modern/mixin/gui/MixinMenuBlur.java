// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.mixin.gui;

import io.github.arcadiumclient.modern.gui.ModernScreens;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class MixinMenuBlur {
    @Inject(method = "getMenuBackgroundBlurriness", at = @At("HEAD"), cancellable = true)
    private void arcadium$blur(CallbackInfoReturnable<Integer> callback) {
        Integer radius = ModernScreens.backgroundBlur();
        if (radius != null) callback.setReturnValue(radius);
    }
}
