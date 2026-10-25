// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.modern.client.features.LegacyCrosshairTweaks;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("polycrosshair")
@Mixin(targets = "org.polyfrost.crosshair.render.CrosshairRenderer", remap = false)
public abstract class LegacyCrosshairProfileMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void arcadium$legacyVanilla(GuiGraphics graphics, boolean spriteReached, CallbackInfoReturnable<Boolean> cir) {
        if (LegacyCrosshairTweaks.active()) cir.setReturnValue(false);
    }
}
