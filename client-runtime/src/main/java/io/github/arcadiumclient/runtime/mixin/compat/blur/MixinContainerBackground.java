// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.compat.blur;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import io.github.arcadiumclient.runtime.client.features.BackgroundOpacity;
import io.github.arcadiumclient.runtime.client.features.ContainerBackgroundOpacity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "eu.midnightdust.blur.BlurRenderer", remap = false)
public class MixinContainerBackground {
    @Shadow private static float backgroundProgress;

    @ModifyReturnValue(method = "color", at = @At("RETURN"), remap = false)
    private static int arcadium$containerOpacity(int color, boolean bottom) {
        if (!(Minecraft.getInstance().screen instanceof InventoryMenuScreen) || !ContainerBackgroundOpacity.available()) return color;
        return BackgroundOpacity.withAlpha(color,
            BackgroundOpacity.containerAlpha(ContainerBackgroundOpacity.percent(), bottom, backgroundProgress));
    }
}
