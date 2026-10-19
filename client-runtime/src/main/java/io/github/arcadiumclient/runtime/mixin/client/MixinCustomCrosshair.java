// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import io.github.arcadiumclient.runtime.client.features.CustomCrosshairRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiElement.class, priority = 2500)
public abstract class MixinCustomCrosshair {
    @Inject(method = "drawTexture(IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void arcadium$customCrosshair(int x, int y, int u, int v, int width, int height, CallbackInfo ci) {
        if (u == 0 && v == 0 && width == 16 && height == 16 && (Object) this == Minecraft.getInstance().gui
                && CustomCrosshairRenderer.render()) ci.cancel();
    }
}
