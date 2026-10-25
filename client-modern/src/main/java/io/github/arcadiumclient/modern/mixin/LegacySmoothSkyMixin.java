// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.arcadiumclient.modern.client.profile.LegacySmoothSkyOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkyRenderer.class)
public abstract class LegacySmoothSkyMixin {
    @Inject(method = "shouldRenderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void arcadium$voidDisc(float partialTick, ClientLevel level, CallbackInfoReturnable<Boolean> cir) {
        if (LegacySmoothSkyOptions.removeVoidBox()) cir.setReturnValue(false);
    }
    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void arcadium$sunrise(PoseStack pose, float sunAngle, int color, CallbackInfo ci) {
        if (LegacySmoothSkyOptions.removeSunriseColor()) ci.cancel();
    }
}
