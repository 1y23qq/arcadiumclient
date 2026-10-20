// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.LegacyCrosshair040Qa;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.GuiElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiElement.class)
public abstract class LegacyCrosshairProbe {
    @Inject(method = "drawTexture(IIIIII)V", at = @At("HEAD"))
    private void arcadium$crosshair(int x, int y, int u, int v, int width, int height, CallbackInfo callback) {
        if ((Object) this instanceof GameGui && u == 0 && v == 0 && width == 16 && height == 16) LegacyCrosshair040Qa.vanillaDraw();
    }
}
