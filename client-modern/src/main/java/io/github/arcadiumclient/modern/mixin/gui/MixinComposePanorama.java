// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.mixin.gui;

import io.github.arcadiumclient.modern.gui.ModernScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinComposePanorama {
    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void arcadium$panorama(GuiGraphics graphics, float delta, CallbackInfo callback) {
        if ((Object) this instanceof ComposeScreen && Minecraft.getInstance().level == null) {
            Screen screen = (Screen) (Object) this;
            ModernScreens.drawPanorama(graphics, screen.width, screen.height);
            callback.cancel();
        }
    }
}
