// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.debug.CustomF3Renderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public abstract class CustomF3OverlayMixin {
    @Inject(method = "renderLines", at = @At("HEAD"), cancellable = true)
    private void arcadium$customText(GuiGraphics graphics, java.util.List<String> lines, boolean right, CallbackInfo callback) {
        if (CustomF3Renderer.active()) { io.github.arcadiumclient.modern.client.debug.CustomF3Data.capture(lines,!right); callback.cancel(); }
    }
    @Inject(method = "render", at = @At("HEAD"))
    private void arcadium$begin(CallbackInfo callback) { if (CustomF3Renderer.active()) io.github.arcadiumclient.modern.client.debug.CustomF3Data.beginFrame(); }
    @Inject(method = "render3dCrosshair", at = @At("HEAD"), cancellable = true)
    private void arcadium$axes(CallbackInfo callback) {
        if (CustomF3Renderer.active() && io.github.arcadiumclient.modern.client.features.CustomF3Config.getHideDebugCrosshair()) callback.cancel();
    }
}
