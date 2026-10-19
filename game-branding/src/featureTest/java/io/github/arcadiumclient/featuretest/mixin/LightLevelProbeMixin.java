package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.FeatureQa028;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.arcadiumclient.runtime.client.features.LightLevelOverlay", remap = false)
public abstract class LightLevelProbeMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private static void arcadium$beforeLight(float tickDelta, CallbackInfo ci) { FeatureQa028.beginLight(); }

    @Inject(method = "render", at = @At("RETURN"))
    private static void arcadium$afterLight(float tickDelta, CallbackInfo ci) { FeatureQa028.endLight(); }
}
