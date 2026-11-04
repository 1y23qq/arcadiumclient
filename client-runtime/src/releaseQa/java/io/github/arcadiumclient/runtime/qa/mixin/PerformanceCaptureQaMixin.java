// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.PerformanceCaptureQa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class PerformanceCaptureQaMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadium$testPerformanceCapture(CallbackInfo callback) { PerformanceCaptureQa.tick(); }
}
