package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.ForgeQa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.polyfrost.oneconfig.internal.OneConfig", remap = false)
public abstract class ArcadiumHudProbeMixin {
    @Inject(method = "render()V", at = @At("HEAD"))
    private static void arcadium$hudFrame(CallbackInfo callback) {
        if (ForgeQa.enabled()) ForgeQa.recordNativeHud();
    }
}
