// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration.mixin;

import io.github.arcadiumclient.modern.client.migration.ModernLegacyNativeProfiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "eu.midnightdust.lib.config.MidnightConfig", remap = false)
public class BlurPersistenceMixin {
    @Shadow protected String modid;
    @Inject(method = "writeChanges()V", at = @At("HEAD"), cancellable = true)
    private void arcadium$saveLegacySchema(CallbackInfo callback) {
        if (!"blur".equals(modid)) return;
        ModernLegacyNativeProfiles.captureBlurSave();
        callback.cancel();
    }
    @Inject(method = "loadValuesFromJson", at = @At("HEAD"), cancellable = true)
    private void arcadium$keepCurrentProfile(CallbackInfo callback) {
        if ("blur".equals(modid) && ModernLegacyNativeProfiles.skipNativeBlurLoad()) callback.cancel();
    }
}
