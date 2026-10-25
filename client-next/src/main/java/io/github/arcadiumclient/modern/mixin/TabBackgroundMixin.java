// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.modern.client.features.BackgroundOpacity;
import io.github.arcadiumclient.modern.client.features.TabBackgroundConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public abstract class TabBackgroundMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void arcadium$opacity(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color, Operation<Void> original) {
        if (TabBackgroundConfig.getEnabled()) color = BackgroundOpacity.withAlpha(color, TabBackgroundConfig.getOpacity());
        if ((color >>> 24) != 0) original.call(graphics, left, top, right, bottom, color);
    }
}
