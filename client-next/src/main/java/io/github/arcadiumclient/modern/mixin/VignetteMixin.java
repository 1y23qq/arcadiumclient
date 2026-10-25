// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.modern.client.features.VignetteConfig;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class VignetteMixin {
    @ModifyExpressionValue(method = "extractVignette", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;vignetteBrightness:F"))
    private float arcadium$strength(float original) { return VignetteConfig.strength(original); }
    @ModifyExpressionValue(method = "extractVignette", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I"))
    private int arcadium$statusTint(int original) { return VignetteConfig.tint(original); }
}
