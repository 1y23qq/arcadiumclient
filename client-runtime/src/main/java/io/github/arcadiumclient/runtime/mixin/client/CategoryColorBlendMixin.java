// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.colorsaturation.CategoryColorRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class CategoryColorBlendMixin {
    @Inject(method = "enableBlend()V", at = @At("RETURN"))
    private static void arcadium$enableCoverage(CallbackInfo callback) { CategoryColorRenderer.blendEnabled(true); }

    @Inject(method = "disableBlend()V", at = @At("RETURN"))
    private static void arcadium$disableCoverage(CallbackInfo callback) { CategoryColorRenderer.blendEnabled(false); }

    @Inject(method = "blendFunc(II)V", at = @At("RETURN"))
    private static void arcadium$blendCoverage(int source, int destination, CallbackInfo callback) {
        CategoryColorRenderer.blendFunction(source, destination, source, destination);
    }

    @Inject(method = "blendFuncSeparate(IIII)V", at = @At("RETURN"))
    private static void arcadium$separateCoverage(int source, int destination, int sourceAlpha, int destinationAlpha, CallbackInfo callback) {
        CategoryColorRenderer.blendFunction(source, destination, sourceAlpha, destinationAlpha);
    }

    @Inject(method = "colorMask(ZZZZ)V", at = @At("RETURN"))
    private static void arcadium$colorCoverage(boolean red, boolean green, boolean blue, boolean alpha, CallbackInfo callback) {
        CategoryColorRenderer.colorMask(red, green, blue, alpha);
    }
}
