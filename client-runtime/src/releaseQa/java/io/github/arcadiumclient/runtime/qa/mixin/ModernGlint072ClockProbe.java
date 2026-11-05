// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ModernGlint072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.polyfrost.overflowanimations.legacy.tint.LegacyGlint", remap = false)
public abstract class ModernGlint072ClockProbe {
    @ModifyVariable(method = "setupModernTexturing", at = @At("STORE"), ordinal = 0, remap = false)
    private static long modern$clock(long time, float scale, double speed) { return ModernGlint072Qa.clock(time, speed); }

    @Inject(method = "setupModernTexturing", at = @At("RETURN"), remap = false)
    private static void modern$matrix(float scale, double speed, CallbackInfo callback) { ModernGlint072Qa.textureMatrix(); }
}
