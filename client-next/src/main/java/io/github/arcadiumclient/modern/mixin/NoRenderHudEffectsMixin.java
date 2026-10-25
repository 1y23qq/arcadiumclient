// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.Effect;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class NoRenderHudEffectsMixin {
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void arcadium$vignette(CallbackInfo ci) { if (NoRender.effect(Effect.VIGNETTE)) ci.cancel(); }
    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void arcadium$portal(CallbackInfo ci) { if (NoRender.effect(Effect.PORTAL_OVERLAY)) ci.cancel(); }
    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void arcadium$pumpkin(GuiGraphicsExtractor graphics, Identifier image, float opacity, CallbackInfo ci) {
        if (NoRender.effect(Effect.PUMPKIN_OVERLAY) && image.getPath().contains("pumpkin")) ci.cancel();
    }
}
