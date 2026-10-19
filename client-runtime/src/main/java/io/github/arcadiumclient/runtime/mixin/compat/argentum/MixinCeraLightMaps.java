// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.rdh.cera.modules.colors.LightMaps", remap = false)
public class MixinCeraLightMaps {
    @ModifyArg(
            method = "channel(FFZ)I",
            at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/api/util/ColorU8;normalizedFloatToByte(F)I"),
            index = 0,
            remap = false
    )
    private static float arcadium$clampLightmapColor(float color) {
        // High gamma can overflow the wrapping byte conversion.
        return Math.max(0.0F, Math.min(1.0F, color));
    }
}
