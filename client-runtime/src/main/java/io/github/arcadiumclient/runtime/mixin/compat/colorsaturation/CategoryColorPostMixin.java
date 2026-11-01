// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.colorsaturation;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.colorsaturation.CategoryColorRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("colorsaturation")
@Mixin(targets = "org.polyfrost.colorsaturation.client.SaturationHandler", remap = false)
public abstract class CategoryColorPostMixin {
    @Inject(method = "update()V", at = @At("HEAD"), cancellable = true)
    private static void arcadium$skipCategoryAllocation(CallbackInfo callback) { if (CategoryColorRenderer.skipLegacy()) callback.cancel(); }

    @Inject(method = "render(F)V", at = @At("HEAD"), cancellable = true)
    private static void arcadium$skipCategoryPost(float delta, CallbackInfo callback) { if (CategoryColorRenderer.skipLegacy()) callback.cancel(); }
}
