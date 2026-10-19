// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.vanillahud;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.arcadiumclient.runtime.client.features.BackgroundOpacity;
import io.github.arcadiumclient.runtime.client.features.TabBackgroundConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "org.polyfrost.vanillahud.hud.TabListHud", remap = false)
public class MixinTabListBackground {
    @ModifyReturnValue(method = {"getHeaderBgArgb", "getBodyBgArgb", "getFooterBgArgb", "getTabWidgetArgb"}, at = @At("RETURN"), remap = false)
    private int arcadium$tabOpacity(int color) {
        return TabBackgroundConfig.getEnabled() ? BackgroundOpacity.withAlpha(color, TabBackgroundConfig.getOpacity()) : color;
    }
}
