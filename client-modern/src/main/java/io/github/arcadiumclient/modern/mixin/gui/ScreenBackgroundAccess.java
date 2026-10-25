// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenBackgroundAccess {
    @Invoker("renderBlurredBackground") void arcadium$blurBackground(GuiGraphics graphics);
}
