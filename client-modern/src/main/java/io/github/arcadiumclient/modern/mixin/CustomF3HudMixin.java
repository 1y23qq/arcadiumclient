// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.debug.CustomF3Renderer;
import io.github.arcadiumclient.modern.client.features.CustomF3Config;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class CustomF3HudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void arcadium$draw(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) { CustomF3Renderer.render(graphics); }
    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void arcadium$sidebar(CallbackInfo callback) { if (CustomF3Renderer.showing() && CustomF3Config.getHideSidebar()) callback.cancel(); }
    @Inject(method = "renderBossOverlay", at = @At("HEAD"), cancellable = true)
    private void arcadium$bossbar(CallbackInfo callback) { if (CustomF3Renderer.showing() && CustomF3Config.getHideBossbar()) callback.cancel(); }
}
