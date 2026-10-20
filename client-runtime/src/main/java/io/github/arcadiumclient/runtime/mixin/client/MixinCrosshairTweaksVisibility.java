// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import io.github.arcadiumclient.runtime.client.features.CustomCrosshairRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameGui.class)
public abstract class MixinCrosshairTweaksVisibility {
    @WrapMethod(method = "hasCrosshair")
    private boolean arcadium$crosshairVisibility(Operation<Boolean> original) {
        Minecraft client = Minecraft.getInstance();
        int perspective = client.options.perspective;
        boolean eligible;
        if (perspective != 0 && CustomCrosshairRenderer.showInThirdPerson()) {
            client.options.perspective = 0;
            try {
                eligible = original.call();
            } finally {
                client.options.perspective = perspective;
            }
        } else eligible = original.call();
        return CustomCrosshairRenderer.visible(eligible);
    }
}
