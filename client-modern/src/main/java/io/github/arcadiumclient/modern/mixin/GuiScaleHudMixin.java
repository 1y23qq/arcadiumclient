// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.modern.client.features.GuiScaling;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Gui.class)
public abstract class GuiScaleHudMixin {
    @WrapMethod(method = "renderHotbarAndDecorations")
    private void arcadium$hotbar(GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        transform(graphics, delta, GuiScaling.hotbarRatio(), graphics.guiHeight(), original);
    }
    @WrapMethod(method = "renderCrosshair")
    private void arcadium$crosshair(GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        transform(graphics, delta, GuiScaling.crosshairRatio(), graphics.guiHeight() / 2f, original);
    }
    private static void transform(GuiGraphics graphics, DeltaTracker delta, float ratio, float anchorY, Operation<Void> original) {
        if (ratio == 1) { original.call(graphics, delta); return; }
        float anchorX = graphics.guiWidth() / 2f;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(anchorX, anchorY).scale(ratio).translate(-anchorX, -anchorY);
            original.call(graphics, delta);
        } finally { graphics.pose().popMatrix(); }
    }
}
