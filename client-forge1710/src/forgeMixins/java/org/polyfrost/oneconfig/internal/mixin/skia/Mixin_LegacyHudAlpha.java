// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.mixin.skia;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.shader.Framebuffer", priority = 1100)
public class Mixin_LegacyHudAlpha {
    @WrapWithCondition(method = "func_147615_c(II)V", at = @At(value = "INVOKE",
        target = "Lcom/gtnewhorizons/angelica/glsm/GLStateManager;disableBlend()V"), remap = false)
    private boolean arcadium$hudBlend() {
        return !SkiaCtx.isLegacyHudTarget(this);
    }
}
