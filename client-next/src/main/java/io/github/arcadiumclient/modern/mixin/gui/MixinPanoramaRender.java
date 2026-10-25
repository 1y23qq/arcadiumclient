// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.mixin.gui;

import io.github.arcadiumclient.modern.gui.ModernScreens;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.CubeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class MixinPanoramaRender {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(FF)V"))
    private void arcadium$panorama(CubeMap fallback, float pitch, float yaw) {
        ModernScreens.renderPanorama(fallback, pitch, yaw);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void arcadium$close(CallbackInfo callback) { ModernScreens.closePanorama(); }
}
