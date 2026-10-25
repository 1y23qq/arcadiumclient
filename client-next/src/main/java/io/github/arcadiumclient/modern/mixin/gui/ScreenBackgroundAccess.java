// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenBackgroundAccess {
    @Invoker("extractBlurredBackground") void arcadium$blurBackground(GuiGraphicsExtractor graphics);
}
