// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.GameGui;
import io.github.arcadiumclient.runtime.client.features.CustomCrosshairRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameGui.class)
public abstract class MixinCrosshairTweaksVisibility {
    @ModifyReturnValue(method = "hasCrosshair", at = @At("RETURN"))
    private boolean arcadium$crosshairVisibility(boolean vanilla) {
        return CustomCrosshairRenderer.visible(vanilla);
    }
}
