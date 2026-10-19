// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.damagetint;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@IfModLoaded("damagetint")
@Mixin(targets = "org.polyfrost.damagetint.client.DamageTintConfig", remap = false)
public abstract class MixinDamageTintConfig {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/polyfrost/oneconfig/api/config/v1/Config;<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lorg/polyfrost/oneconfig/api/config/v1/Config$Category;)V"), index = 2)
    private static String arcadium$moduleTitle(String original) {
        return "Hit Color";
    }
}
