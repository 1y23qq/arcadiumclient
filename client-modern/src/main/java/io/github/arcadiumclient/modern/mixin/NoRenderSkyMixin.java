// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.NoRender;
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.Effect;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class NoRenderSkyMixin {
    @Inject(method = {"renderSkyDisc", "renderDarkDisc", "renderSunMoonAndStars", "renderSunriseAndSunset", "renderEndSky", "renderEndFlash"}, at = @At("HEAD"), cancellable = true)
    private void arcadium$render(CallbackInfo ci) { if (NoRender.effect(Effect.SKY)) ci.cancel(); }
}
