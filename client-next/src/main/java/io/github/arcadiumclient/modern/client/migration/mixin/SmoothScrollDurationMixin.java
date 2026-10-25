// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration.mixin;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.modern.client.migration.LegacySmoothScrollingConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@IfModLoaded("smoothscrollingrefurbished")
@Mixin(targets = "me.wolfii.smoothscrollingrefurbished.ScrollMath", remap = false)
public abstract class SmoothScrollDurationMixin {
    @ModifyVariable(method = "scrollbarVelocity", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static double arcadium$duration(double time) {
        float duration = LegacySmoothScrollingConfig.animationDuration;
        return time / (Float.isFinite(duration) ? Math.clamp(duration, .1f, 5f) : 1);
    }
}
