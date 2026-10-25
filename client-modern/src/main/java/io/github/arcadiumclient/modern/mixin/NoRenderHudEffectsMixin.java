// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.Effect;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class NoRenderHudEffectsMixin {
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void arcadium$vignette(CallbackInfo ci) { if (NoRender.effect(Effect.VIGNETTE)) ci.cancel(); }
    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void arcadium$portal(CallbackInfo ci) { if (NoRender.effect(Effect.PORTAL_OVERLAY)) ci.cancel(); }
    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void arcadium$pumpkin(GuiGraphics graphics, Identifier image, float opacity, CallbackInfo ci) {
        if (NoRender.effect(Effect.PUMPKIN_OVERLAY) && image.getPath().contains("pumpkin")) ci.cancel();
    }
}
