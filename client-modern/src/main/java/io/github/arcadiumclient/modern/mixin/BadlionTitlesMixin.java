// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.modern.client.features.AnimationsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Gui.class)
public abstract class BadlionTitlesMixin {
    @WrapMethod(method = "renderTitle")
    private void arcadium$title(GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        var settings = AnimationsConfig.badlion();
        if (settings == null) { original.call(graphics, delta); return; }
        if (settings.removeTitles()) return;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(settings.titleOffsetX(graphics.guiWidth()), settings.titleOffsetY(graphics.guiHeight()));
            original.call(graphics, delta);
        } finally { graphics.pose().popMatrix(); }
    }
}
