package io.github.arcadiumclient.featuretest.mixin;

import io.github.arcadiumclient.featuretest.PerformanceQa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class PerformanceTestMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadium$performanceTick(CallbackInfo callback) { PerformanceQa.tick(); }

    @Inject(method = "runGame", at = @At("TAIL"))
    private void arcadium$performanceFrame(CallbackInfo callback) { PerformanceQa.afterFrame(); }
}
