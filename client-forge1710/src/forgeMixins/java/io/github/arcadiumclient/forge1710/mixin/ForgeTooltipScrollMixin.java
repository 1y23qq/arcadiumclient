// SPDX-License-Identifier: MIT
// Adapted from Tooltip Scroll. Copyright (c) 2021-2026 Provismet.
package io.github.arcadiumclient.forge1710.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.provismet.tooltipscroll.Options;
import com.provismet.tooltipscroll.ScrollTracker;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.GuiScreen")
public abstract class ForgeTooltipScrollMixin {
    private static final String TOOLTIP = "drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V";

    @Inject(method = TOOLTIP, at = @At("HEAD"), remap = false)
    private void arcadium$start(List<String> text, int mouseX, int mouseY, @Coerce Object font, CallbackInfo callback) {
        ScrollTracker.unlock();
        ScrollTracker.update();
        ScrollTracker.setItem(text);
        if (Options.matrixMode) {
            GL11.glPushMatrix();
            GL11.glTranslated(ScrollTracker.getXOffset(), ScrollTracker.getYOffset(), 0);
        }
    }

    @Inject(method = TOOLTIP, at = @At(value = "CONSTANT", args = "intValue=-267386864"), remap = false)
    private void arcadium$position(List<String> text, int mouseX, int mouseY, @Coerce Object font, CallbackInfo callback,
                                  @Local(ordinal = 3) LocalIntRef x, @Local(ordinal = 4) LocalIntRef y) {
        if (Options.matrixMode) return;
        x.set(x.get() + ScrollTracker.getXOffset());
        y.set(y.get() + ScrollTracker.getYOffset());
        if (Options.startOnTop && !ScrollTracker.hasMoved() && y.get() < 4) {
            int previous = y.get();
            y.set(4);
            ScrollTracker.setInitialYOffset(4 - previous);
        }
    }

    @Inject(method = TOOLTIP, at = @At("RETURN"), remap = false)
    private void arcadium$finish(List<String> text, int mouseX, int mouseY, @Coerce Object font, CallbackInfo callback) {
        if (Options.matrixMode) GL11.glPopMatrix();
    }
}
