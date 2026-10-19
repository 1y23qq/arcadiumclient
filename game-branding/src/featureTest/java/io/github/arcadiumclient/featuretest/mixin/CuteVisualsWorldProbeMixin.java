package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.CuteVisuals035Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.arcadiumclient.runtime.client.features.CuteVisuals", remap = false)
public abstract class CuteVisualsWorldProbeMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private static void arcadium$beforeCuteWorld(float tickDelta, CallbackInfo ci) {
        CuteVisuals035Qa.beforeWorldDraw(tickDelta);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void arcadium$afterCuteWorld(float tickDelta, CallbackInfo ci) {
        CuteVisuals035Qa.afterWorldDraw(tickDelta);
    }
}
