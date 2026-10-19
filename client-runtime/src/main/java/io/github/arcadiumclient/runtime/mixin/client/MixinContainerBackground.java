// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import io.github.arcadiumclient.runtime.client.features.ContainerBackgroundOpacity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Screen.class)
public class MixinContainerBackground {
    @WrapMethod(method = "renderBackground(I)V")
    private void arcadium$hideContainerDim(int offset, Operation<Void> original) {
        if ((Object) this instanceof InventoryMenuScreen && Minecraft.getInstance().world != null
            && ContainerBackgroundOpacity.available() && ContainerBackgroundOpacity.percent() == 0) return;
        original.call(offset);
    }
}
