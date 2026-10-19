// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.fullbright;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.features.PhysicalLightGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@IfModLoaded("fullbright")
@Mixin(targets = "org.polyfrost.fullbright.legacy.LegacyLight", remap = false)
public abstract class MixinFullBrightLight {
    @ModifyReturnValue(method = "shouldBrighten", at = @At("RETURN"))
    private static boolean arcadium$renderLightOnly(boolean original) {
        return original && !PhysicalLightGuard.active();
    }
}
