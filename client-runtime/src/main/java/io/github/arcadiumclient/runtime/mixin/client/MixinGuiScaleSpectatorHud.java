// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.spectator.SpectatorGui;
import net.minecraft.client.render.Window;
import io.github.arcadiumclient.runtime.client.features.LegacyGuiScaleTransform;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SpectatorGui.class)
public abstract class MixinGuiScaleSpectatorHud {
    @WrapMethod(method = "renderHotbar")
    private void arcadium$spectatorHotbar(Window window, float tickDelta, Operation<Void> original) {
        boolean pushed = LegacyGuiScaleTransform.hotbar(window);
        try { original.call(window, tickDelta); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }

    @WrapMethod(method = "renderTooltip")
    private void arcadium$spectatorTooltip(Window window, Operation<Void> original) {
        boolean pushed = LegacyGuiScaleTransform.hotbar(window);
        try { original.call(window); }
        finally { LegacyGuiScaleTransform.pop(pushed); }
    }
}
