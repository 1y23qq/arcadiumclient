// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration.mixin;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.modern.client.migration.ModernLegacyNativeProfiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("smoothscrollingrefurbished")
@Mixin(targets = "me.wolfii.smoothscrollingrefurbished.config.Config", remap = false)
public abstract class SmoothScrollSaveMixin {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true, remap = false)
    private static void arcadium$profile(CallbackInfo ci) { if (ModernLegacyNativeProfiles.captureScrollingSave()) ci.cancel(); }
}
