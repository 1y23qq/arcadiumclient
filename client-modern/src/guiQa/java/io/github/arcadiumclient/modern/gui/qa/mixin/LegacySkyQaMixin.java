// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa.mixin;

import io.github.arcadiumclient.modern.gui.qa.NativeLegacySkyProbe;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkyRenderer.class, priority = 2000)
abstract class LegacySkyQaMixin {
    @Inject(method = "renderSkyDisc", at = @At("RETURN"))
    private void arcadium$sampleSky(int color, CallbackInfo ci) { NativeLegacySkyProbe.sample((SkyRenderer) (Object) this); }
    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"))
    private void arcadium$entered(CallbackInfo ci) { NativeLegacySkyProbe.entered(); }
    @Inject(method = "renderSunriseAndSunset", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;draw(II)V"))
    private void arcadium$draw(CallbackInfo ci) { NativeLegacySkyProbe.draw(); }
    @Inject(method = "renderSunriseAndSunset", at = @At("RETURN"))
    private void arcadium$completed(CallbackInfo ci) { NativeLegacySkyProbe.completed(); }
}
