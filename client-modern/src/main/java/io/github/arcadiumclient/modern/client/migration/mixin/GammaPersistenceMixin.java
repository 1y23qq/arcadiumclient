// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration.mixin;

import io.github.arcadiumclient.modern.client.migration.GammaProfiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.sjouwer.gammautils.GammaUtils", remap = false)
public class GammaPersistenceMixin {
    @Inject(method = "saveConfig", at = @At("HEAD"), cancellable = true)
    private static void arcadium$saveToActiveLegacyProfile(CallbackInfo callback) {
        if (GammaProfiles.ready()) { GammaProfiles.saveNativeState(); callback.cancel(); }
    }
}
