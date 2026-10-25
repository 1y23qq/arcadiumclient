// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.modern.client.features.VignetteConfig;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class VignetteMixin {
    @ModifyExpressionValue(method = "renderVignette", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;vignetteBrightness:F"))
    private float arcadium$strength(float original) { return VignetteConfig.strength(original); }
    @ModifyExpressionValue(method = "renderVignette", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I"))
    private int arcadium$statusTint(int original) { return VignetteConfig.tint(original); }
}
