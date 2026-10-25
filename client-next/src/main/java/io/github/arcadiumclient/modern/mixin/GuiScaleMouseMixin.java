// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.arcadiumclient.modern.client.features.GuiScaling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public abstract class GuiScaleMouseMixin {
    @Inject(method = {"getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D", "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D"}, at = @At("RETURN"), cancellable = true)
    private static void arcadium$mouse(Window window, double original, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(cir.getReturnValue() / GuiScaling.ratio(Minecraft.getInstance().gui.screen()));
    }
}
