// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import io.github.arcadiumclient.runtime.client.features.GuiScaling;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Screen.class)
public abstract class MixinGuiScaleScreen {
    @WrapMethod(method = "init(Lnet/minecraft/client/Minecraft;II)V")
    private void arcadium$containerDimensions(Minecraft minecraft, int width, int height, Operation<Void> original) {
        Screen screen = (Screen) (Object) this;
        if (GuiScaling.isContainer(screen) && GuiScaling.enabled()) {
            width = GuiScaling.screenWidth(screen);
            height = GuiScaling.screenHeight(screen);
        }
        original.call(minecraft, width, height);
    }

}
