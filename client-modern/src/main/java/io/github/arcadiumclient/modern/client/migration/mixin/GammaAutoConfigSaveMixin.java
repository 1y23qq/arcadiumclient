// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration.mixin;

import io.github.arcadiumclient.modern.client.migration.GammaProfiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.shedaniel.autoconfig.serializer.GsonConfigSerializer", remap = false)
public class GammaAutoConfigSaveMixin {
    @Inject(method = "serialize", at = @At("HEAD"), cancellable = true)
    private void arcadium$preserveOtherProfiles(@Coerce Object config, CallbackInfo callback) {
        if (config.getClass().getName().equals("io.github.sjouwer.gammautils.config.ModConfig") && GammaProfiles.ready()) {
            GammaProfiles.saveNativeState(); callback.cancel();
        }
    }
}
