// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.modern.client.features.GuiScaling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Screen.class)
public abstract class GuiScaleScreenMixin {
    @ModifyVariable(method = "init(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int arcadium$width(int nativeWidth) { return GuiScaling.width((Screen) (Object) this, nativeWidth); }
    @ModifyVariable(method = "init(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int arcadium$height(int nativeHeight) { return GuiScaling.height((Screen) (Object) this, nativeHeight); }
    @WrapMethod(method = "renderWithTooltipAndSubtitles")
    private void arcadium$render(GuiGraphics graphics, int mouseX, int mouseY, float delta, Operation<Void> original) {
        float ratio = GuiScaling.ratio((Screen) (Object) this);
        if (ratio == 1) { original.call(graphics, mouseX, mouseY, delta); return; }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().scale(ratio);
            original.call(graphics, mouseX, mouseY, delta);
        } finally { graphics.pose().popMatrix(); }
    }
}
