// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.diagnostics;

import io.github.arcadiumclient.runtime.client.diagnostics.PerformanceCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 2000)
public abstract class CaptureMinecraftMixin {
    @Inject(method = "runGame", at = @At("HEAD"))
    private void arcadium$captureFrameStart(CallbackInfo callback) { PerformanceCapture.frameBegin(); }
    @Inject(method = "runGame", at = @At("RETURN"))
    private void arcadium$captureFrameEnd(CallbackInfo callback) { PerformanceCapture.frameEnd(); }
    @Inject(method = "tick", at = @At("HEAD"))
    private void arcadium$captureTickStart(CallbackInfo callback) { PerformanceCapture.phaseBegin(0); }
    @Inject(method = "tick", at = @At("RETURN"))
    private void arcadium$captureTickEnd(CallbackInfo callback) { PerformanceCapture.phaseEnd(0); }
    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void arcadium$capturePresentStart(CallbackInfo callback) { PerformanceCapture.phaseBegin(7); }
    @Inject(method = "updateDisplay", at = @At("RETURN"))
    private void arcadium$capturePresentEnd(CallbackInfo callback) { PerformanceCapture.phaseEnd(7); }
    @Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V"))
    private void arcadium$captureCapStart(CallbackInfo callback) { PerformanceCapture.phaseBegin(8); }
    @Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V", shift = At.Shift.AFTER))
    private void arcadium$captureCapEnd(CallbackInfo callback) { PerformanceCapture.phaseEnd(8); }
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void arcadium$captureShutdown(CallbackInfo callback) { PerformanceCapture.shutdown(); }
}
