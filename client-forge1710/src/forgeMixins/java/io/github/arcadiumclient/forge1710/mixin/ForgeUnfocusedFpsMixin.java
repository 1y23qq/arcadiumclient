// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.mixin;

import com.gtnewhorizons.angelica.rendering.FpsReducer;
import io.github.arcadiumclient.runtime.client.features.UnfocusedFrameLimit;
import org.lwjglx.opengl.Display;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FpsReducer.class, remap = false)
public abstract class ForgeUnfocusedFpsMixin {
    @Inject(method = "effectiveCap(IZ)I", at = @At("HEAD"), cancellable = true, require = 1)
    private static void arcadium$unfocusedLimit(int normalLimit, boolean inMenu, CallbackInfoReturnable<Integer> callback) {
        if (!Display.isCreated() || Display.isActive()) return;
        callback.setReturnValue(OneConfigConfig.limitUnfocusedFps
            ? UnfocusedFrameLimit.apply(normalLimit, false, OneConfigConfig.unfocusedFps) : normalLimit);
    }
}
